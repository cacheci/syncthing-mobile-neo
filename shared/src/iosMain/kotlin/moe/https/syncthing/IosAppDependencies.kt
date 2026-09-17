package moe.https.syncthing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moe.https.syncthing.core.BackupController
import moe.https.syncthing.core.BackupImportFormat
import moe.https.syncthing.core.CoreController
import moe.https.syncthing.core.CoreLogContent
import moe.https.syncthing.core.CoreLogReader
import moe.https.syncthing.core.CoreLogSource
import moe.https.syncthing.core.CoreSnapshot
import moe.https.syncthing.core.DevicesController
import moe.https.syncthing.core.FoldersController
import moe.https.syncthing.core.GuiTlsFile
import moe.https.syncthing.core.NewDeviceConfiguration
import moe.https.syncthing.core.NewFolderConfiguration
import moe.https.syncthing.core.RecentChangesController
import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration
import moe.https.syncthing.core.SettingController
import moe.https.syncthing.core.SettingSaveResult
import moe.https.syncthing.core.SettingSnapshot
import moe.https.syncthing.core.SyncthingPendingDevice
import moe.https.syncthing.core.SyncthingPendingFolder

private const val IOS_CORE_UNAVAILABLE = "Syncthing 核心尚未移植到 iOS"

/**
 * Keeps the shared UI honest while the native Syncthing runtime is unavailable on iOS.
 * UI-only settings are still persisted by NSUserDefaultsAppSettingsStorage; operations
 * that require the core fail with a visible, platform-specific message.
 */
internal class IosUnavailablePlatformServices :
    CoreController,
    DevicesController,
    FoldersController,
    RecentChangesController,
    SettingController,
    CoreLogReader,
    BackupController {

    private val mutableSnapshot = MutableStateFlow(
        CoreSnapshot(lastError = IOS_CORE_UNAVAILABLE),
    )

    override val snapshot: StateFlow<CoreSnapshot> = mutableSnapshot

    override fun start() = Unit

    override fun stop() = Unit

    override suspend fun selectCore(id: String): Nothing = unavailable()

    override suspend fun deleteCore(id: String): Nothing = unavailable()

    override suspend fun loadDevices(): Nothing = unavailable()

    override suspend fun loadPendingDevices(): Nothing = unavailable()

    override suspend fun addDevice(configuration: NewDeviceConfiguration): Nothing = unavailable()

    override suspend fun updateDevice(configuration: NewDeviceConfiguration): Nothing = unavailable()

    override suspend fun deleteDevice(deviceId: String): Nothing = unavailable()

    override suspend fun dismissPendingDevice(deviceId: String): Nothing = unavailable()

    override suspend fun ignorePendingDevice(device: SyncthingPendingDevice): Nothing = unavailable()

    override suspend fun loadFolders(): Nothing = unavailable()

    override suspend fun loadPendingFolders(): Nothing = unavailable()

    override suspend fun addFolder(configuration: NewFolderConfiguration): Nothing = unavailable()

    override suspend fun updateFolder(configuration: NewFolderConfiguration): Nothing = unavailable()

    override suspend fun deleteFolder(folderId: String, deleteLocalFiles: Boolean): Nothing = unavailable()

    override suspend fun setFolderPaused(folderId: String, paused: Boolean): Nothing = unavailable()

    override suspend fun dismissPendingFolder(folder: SyncthingPendingFolder): Nothing = unavailable()

    override suspend fun ignorePendingFolder(folder: SyncthingPendingFolder): Nothing = unavailable()

    override suspend fun loadRecentChanges(): Nothing = unavailable()

    override suspend fun loadSetting(): SettingSnapshot = SettingSnapshot(
        configuration = SettingConfiguration.startupDefaults(),
        accessMode = SettingAccessMode.STARTUP_ONLY,
    )

    override suspend fun pingDiscoveryServer(address: String): Nothing = unavailable()

    override suspend fun saveSetting(
        configuration: SettingConfiguration,
        guiTlsFiles: Map<GuiTlsFile, ByteArray>,
    ): SettingSaveResult = unavailable()

    override suspend fun read(source: CoreLogSource): CoreLogContent = CoreLogContent(
        text = IOS_CORE_UNAVAILABLE,
        refreshedAt = "—",
    )

    override suspend fun exportBackup(destinationUri: String, password: String?): Nothing = unavailable()

    override suspend fun importBackup(
        sourceUri: String,
        password: String?,
        format: BackupImportFormat,
    ): Nothing = unavailable()

    private fun unavailable(): Nothing = error(IOS_CORE_UNAVAILABLE)
}
