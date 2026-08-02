package moe.https.syncthing.core

enum class CoreState {
    NOT_INSTALLED,
    STOPPED,
    INSTALLING,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

data class CoreSnapshot(
    val state: CoreState = CoreState.NOT_INSTALLED,
    val version: String? = null,
    val uptimeSeconds: Long? = null,
    val rssBytes: Long? = null,
    val allocatedBytes: Long? = null,
    val systemBytes: Long? = null,
    val goroutines: Int? = null,
    val lastError: String? = null,
)
