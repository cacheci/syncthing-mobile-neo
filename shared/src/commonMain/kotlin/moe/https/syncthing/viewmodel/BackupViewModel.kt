package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.https.syncthing.core.BackupController
import moe.https.syncthing.core.BackupImportFormat
import moe.https.syncthing.ui.model.BackupUiEffect
import moe.https.syncthing.ui.model.BackupUiState
import moe.https.syncthing.ui.model.PendingBackupImport

class BackupViewModel(
    private val controller: BackupController,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackupUiState())
    val uiState = mutableUiState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<BackupUiEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()

    private var pendingExportPassword: String? = null

    fun requestExport(password: String?) {
        if (mutableUiState.value.isWorking) return
        pendingExportPassword = password?.takeIf(String::isNotEmpty)
        mutableEffects.tryEmit(
            BackupUiEffect.CreateDocument(
                suggestedFileName = if (pendingExportPassword == null) {
                    "syncthing-backup.zip"
                } else {
                    "syncthing-backup-encrypted.zip"
                },
            ),
        )
    }

    fun onExportDestinationSelected(destinationUri: String?) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (destinationUri == null) return
        runOperation(successMessage = "备份导出成功") {
            controller.exportBackup(destinationUri, password)
        }
    }

    fun requestImport(format: BackupImportFormat) {
        if (mutableUiState.value.isWorking) return
        mutableEffects.tryEmit(BackupUiEffect.OpenDocument(format))
    }

    fun onImportSourceSelected(sourceUri: String?, format: BackupImportFormat) {
        if (sourceUri == null) return
        mutableUiState.update {
            it.copy(
                pendingImport = PendingBackupImport(sourceUri, format),
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun cancelImport() {
        if (mutableUiState.value.isWorking) return
        mutableUiState.update { it.copy(pendingImport = null) }
    }

    fun confirmImport(password: String?) {
        if (mutableUiState.value.isWorking) return
        val pendingImport = mutableUiState.value.pendingImport ?: return
        runOperation(
            successMessage = "备份导入成功，部分 App 设置将在重启后生效",
            onSuccess = { it.copy(pendingImport = null) },
        ) {
            controller.importBackup(
                sourceUri = pendingImport.sourceUri,
                password = password?.takeIf(String::isNotEmpty),
                format = pendingImport.format,
            )
        }
    }

    fun clearMessage() {
        mutableUiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    private fun runOperation(
        successMessage: String,
        onSuccess: (BackupUiState) -> BackupUiState = { it },
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isWorking = true, successMessage = null, errorMessage = null)
            }
            try {
                operation()
                mutableUiState.update {
                    onSuccess(it.copy(isWorking = false, successMessage = successMessage))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = error.message
                            ?.takeIf(String::isNotBlank)
                            ?: error::class.simpleName
                            ?: "备份操作失败",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(controller: BackupController): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(controller) }
        }
    }
}
