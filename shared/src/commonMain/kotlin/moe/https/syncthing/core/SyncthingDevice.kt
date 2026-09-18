package moe.https.syncthing.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import moe.https.syncthing.ui.theme.AppTheme

data class SyncthingDevice(
    val id: String,
    val name: String?,
    val addresses: List<String>,
    val connected: Boolean,
    val connectionAddress: String?,
    val clientVersion: String?,
    val lastConnectionAt: String?,
    val paused: Boolean,
    val isLocal: Boolean,
    val discoveredAddresses: List<String> = emptyList(),
    val group: String = "",
    val introducer: Boolean = false,
    val autoAcceptFolders: Boolean = false,
    val compression: NewDeviceConfiguration.Compression = NewDeviceConfiguration.Compression.METADATA,
    val numConnections: Int = 0,
    val maxSendKiBPerSecond: Int = 0,
    val maxReceiveKiBPerSecond: Int = 0,
    val untrusted: Boolean = false,
)

@Composable
internal fun SyncthingDevice.displayColor(): Color {
    return if (this.connected) AppTheme.statusColors.ok else AppTheme.statusColors.down
}

data class SyncthingPendingDevice(
    val id: String,
    val name: String?,
    val address: String?,
    val detectedAt: String?,
)

data class NewDeviceConfiguration(
    val deviceId: String,
    val name: String,
    val group: String,
    val addresses: List<String>,
    val introducer: Boolean,
    val autoAcceptFolders: Boolean,
    val compression: Compression,
    val numConnections: Int,
    val maxSendKiBPerSecond: Int,
    val maxReceiveKiBPerSecond: Int,
    val untrusted: Boolean,
) {
    enum class Compression {
        ALL,
        METADATA,
        OFF,
    }
}

data class SyncthingLocalInfo(
    val discoveryEnabled: Boolean,
    val discoveryStatus: List<SyncthingDiscoveryStatus>,
    val listenAddresses: List<SyncthingListenAddress>,
)

data class SyncthingDiscoveryStatus(
    val method: String,
    val error: String?,
)

data class SyncthingListenAddress(
    val address: String,
    val error: String?,
)

data class DevicesSnapshot(
    val devices: List<SyncthingDevice>,
    val pendingDevices: List<SyncthingPendingDevice>,
    val localInfo: SyncthingLocalInfo,
)
