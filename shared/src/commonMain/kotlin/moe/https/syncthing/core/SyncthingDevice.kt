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
)
