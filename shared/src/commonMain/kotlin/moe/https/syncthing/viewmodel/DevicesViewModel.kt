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
import moe.https.syncthing.core.NewDeviceConfiguration
import moe.https.syncthing.ui.model.DevicesUiState
import moe.https.syncthing.ui.model.updateFrom

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
                    mutableUiState.value = mutableUiState.value.updateFrom(controller.loadDevices())
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

    fun addDevice(configuration: NewDeviceConfiguration) {
        val normalizedConfiguration = configuration.copy(
            deviceId = configuration.deviceId.trim(),
            name = configuration.name.trim(),
            group = configuration.group.trim(),
            addresses = configuration.addresses
                .map(String::trim)
                .filter(String::isNotBlank),
        )
        if (normalizedConfiguration.deviceId.isBlank()) {
            mutableUiState.update { it.copy(errorMessage = "设备 ID 不能为空") }
            return
        }
        if (
            normalizedConfiguration.numConnections < 0 ||
            normalizedConfiguration.maxSendKiBPerSecond < 0 ||
            normalizedConfiguration.maxReceiveKiBPerSecond < 0
        ) {
            mutableUiState.update { it.copy(errorMessage = "连接数和速率限制必须是非负整数") }
            return
        }

        viewModelScope.launch {
            refreshMutex.withLock {
                if (mutableUiState.value.isLoading) return@withLock

                mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    controller.addDevice(normalizedConfiguration)
                    mutableUiState.value = mutableUiState.value.updateFrom(controller.loadDevices())
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
        fun factory(controller: DevicesController): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DevicesViewModel(controller)
            }
        }
    }
}
