package moe.https.syncthing.core

import kotlinx.coroutines.flow.StateFlow

interface CoreController {
    val snapshot: StateFlow<CoreSnapshot>

    fun start()

    fun stop()

    suspend fun selectCore(id: String)

    suspend fun deleteCore(id: String)
}

interface DevicesController {
    suspend fun loadDevices(): DevicesSnapshot
    suspend fun loadPendingDevices(): List<SyncthingPendingDevice>

    suspend fun addDevice(configuration: NewDeviceConfiguration)
    suspend fun updateDevice(configuration: NewDeviceConfiguration)
    suspend fun deleteDevice(deviceId: String)
    suspend fun dismissPendingDevice(deviceId: String)
    suspend fun ignorePendingDevice(device: SyncthingPendingDevice)
}

interface FoldersController {
    suspend fun loadFolders(): FoldersSnapshot
    suspend fun loadPendingFolders(): List<SyncthingPendingFolder>

    suspend fun addFolder(configuration: NewFolderConfiguration)
    suspend fun updateFolder(configuration: NewFolderConfiguration)
    suspend fun dismissPendingFolder(folder: SyncthingPendingFolder)
    suspend fun ignorePendingFolder(folder: SyncthingPendingFolder)
}

interface SettingController {
    suspend fun loadSetting(): SettingSnapshot
    suspend fun pingDiscoveryServer(address: String): Long

    suspend fun saveSetting(configuration: SettingConfiguration): SettingSaveResult
}
