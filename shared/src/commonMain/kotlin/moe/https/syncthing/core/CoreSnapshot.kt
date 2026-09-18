package moe.https.syncthing.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import moe.https.syncthing.ui.theme.AppTheme

enum class CoreState {
    NOT_INSTALLED,
    STOPPED,
    INSTALLING,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

@Composable
internal fun CoreState.displayColor(): Color = when (this) {
    CoreState.RUNNING -> AppTheme.statusColors.ok
    CoreState.FAILED -> AppTheme.statusColors.fail
    CoreState.STARTING,
    CoreState.STOPPING,
    CoreState.INSTALLING -> AppTheme.statusColors.pending
    else -> AppTheme.statusColors.down
}

@Composable
internal fun CoreState.displayBackgroundColor(): Color = when (this) {
    CoreState.RUNNING -> AppTheme.statusColors.okContainer
    CoreState.FAILED -> AppTheme.statusColors.failContainer
    CoreState.STARTING,
    CoreState.STOPPING,
    CoreState.INSTALLING -> AppTheme.statusColors.pendingContainer
    else -> AppTheme.statusColors.downContainer
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
    val deviceName: String? = null,
    val uptimeSeconds: Long? = null,
    val downloadBytesPerSecond: Long? = null,
    val uploadBytesPerSecond: Long? = null,
    val downloadedBytes: Long? = null,
    val uploadedBytes: Long? = null,
    val totalFileSizeBytes: Long? = null,
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
