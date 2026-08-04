package moe.https.syncthing.core

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
    val listenAddresses: List<String>,
)

data class SyncthingDiscoveryStatus(
    val method: String,
    val error: String?,
)

data class DevicesSnapshot(
    val devices: List<SyncthingDevice>,
    val localInfo: SyncthingLocalInfo,
)
