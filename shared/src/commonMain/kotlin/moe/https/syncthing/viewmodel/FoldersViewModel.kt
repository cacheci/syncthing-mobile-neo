package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.https.syncthing.core.FoldersController
import moe.https.syncthing.core.NewFolderConfiguration
import moe.https.syncthing.ui.model.FoldersUiState
import moe.https.syncthing.ui.model.updateFrom

class FoldersViewModel(
    private val controller: FoldersController,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FoldersUiState())
    val uiState: StateFlow<FoldersUiState> = mutableUiState.asStateFlow()
    private val refreshMutex = Mutex()

    fun refresh() {
        viewModelScope.launch {
            refreshMutex.withLock {
                if (mutableUiState.value.isLoading) return@withLock

                mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    mutableUiState.value = mutableUiState.value.updateFrom(controller.loadFolders())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            errorMessage = error.message
                                ?.takeIf(String::isNotBlank)
                                ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
        }
    }

    fun addFolder(configuration: NewFolderConfiguration) {
        saveFolder(configuration, updating = false)
    }

    fun updateFolder(configuration: NewFolderConfiguration) {
        saveFolder(configuration, updating = true)
    }

    private fun saveFolder(configuration: NewFolderConfiguration, updating: Boolean) {
        val normalizedConfiguration = configuration.copy(
            folderId = configuration.folderId.trim(),
            label = configuration.label.trim(),
            group = configuration.group.trim(),
            path = configuration.path.trim(),
            devices = configuration.devices.map { device ->
                device.copy(deviceId = device.deviceId.trim())
            },
            availableDeviceIds = configuration.availableDeviceIds.map(String::trim).toSet(),
        )
        val validationMessage = when {
            normalizedConfiguration.folderId.isBlank() -> "文件夹 ID 不能为空"
            normalizedConfiguration.label.isBlank() -> "文件夹名称不能为空"
            normalizedConfiguration.path.isBlank() -> "文件夹路径不能为空"
            normalizedConfiguration.versioningCleanoutDays < 0 ||
                normalizedConfiguration.versioningKeep < 0 ||
                normalizedConfiguration.versioningCleanupIntervalSeconds < 0 ||
                normalizedConfiguration.rescanIntervalSeconds < 0 -> "时间和数量设置必须是非负整数"
            normalizedConfiguration.versioningCleanupIntervalSeconds > 31_536_000 ->
                "定期清除间隔不能超过一年"
            else -> null
        }
        if (validationMessage != null) {
            mutableUiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        viewModelScope.launch {
            refreshMutex.withLock {
                if (mutableUiState.value.isLoading) return@withLock

                mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    if (updating) controller.updateFolder(normalizedConfiguration)
                    else controller.addFolder(normalizedConfiguration)
                    mutableUiState.value = mutableUiState.value.updateFrom(controller.loadFolders())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                                ?.takeIf(String::isNotBlank)
                                ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(controller: FoldersController): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FoldersViewModel(controller)
            }
        }
    }
}
