package moe.https.syncthing.core

import kotlinx.coroutines.flow.StateFlow

interface CoreController {
    val snapshot: StateFlow<CoreSnapshot>

    fun start()

    fun stop()
}

interface DevicesController {
    suspend fun loadDevices(): DevicesSnapshot

    suspend fun addDevice(configuration: NewDeviceConfiguration)
    suspend fun updateDevice(configuration: NewDeviceConfiguration)
}

interface SettingController {
    suspend fun loadSetting(): SettingSnapshot

    suspend fun saveSetting(configuration: SettingConfiguration): SettingSaveResult
}
