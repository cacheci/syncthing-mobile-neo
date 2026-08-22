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

enum class CoreSource {
    BUILT_IN,
    EXTERNAL,
}

enum class CoreAvailability {
    AVAILABLE,
    MISSING,
    EXECUTION_UNSUPPORTED,
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
    val operationMessage: String? = null,
    val selectedCoreId: String = "builtin",
    val selectedCoreSource: CoreSource? = null,
    val availableCores: List<CoreOption> = emptyList(),
    val canSelectCore: Boolean = false,
)

data class CoreOption(
    val id: String,
    val internal: Boolean,
    val version: String,
    val source: CoreSource,
    val availability: CoreAvailability = CoreAvailability.AVAILABLE,
    val unavailableReason: String? = null,
)
