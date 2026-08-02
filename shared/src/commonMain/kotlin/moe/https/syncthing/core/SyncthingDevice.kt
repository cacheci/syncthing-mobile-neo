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
