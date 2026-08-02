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
import moe.https.syncthing.core.DevicesController
import moe.https.syncthing.ui.model.DevicesUiState

class DevicesViewModel(
    private val controller: DevicesController,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = mutableUiState.asStateFlow()
    private val refreshMutex = Mutex()

    fun refresh() {
        viewModelScope.launch {
            refreshMutex.withLock {
                if (mutableUiState.value.isLoading) return@withLock

                mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val devices = controller.loadDevices()
                    mutableUiState.value = DevicesUiState(
                        devices = devices,
                        hasLoaded = true,
                    )
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

    companion object {
        fun factory(controller: DevicesController): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DevicesViewModel(controller)
            }
        }
    }
}
