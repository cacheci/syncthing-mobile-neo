package moe.https.syncthing.ui.model

import moe.https.syncthing.core.CoreSnapshot
import moe.https.syncthing.core.CoreSource
import moe.https.syncthing.core.CoreState

data class CoreUiState(
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
    val availableCores: List<moe.https.syncthing.core.CoreOption> = emptyList(),
    val canSelectCore: Boolean = false,
) {
    val isStarted: Boolean
        get() = state != CoreState.STOPPED

    val canAction: Boolean
        get() = state in setOf(
            CoreState.STOPPED,
            CoreState.STARTING,
            CoreState.RUNNING,
            CoreState.FAILED,
        )

    val canImportCore: Boolean
        get() = state !in setOf(
            CoreState.INSTALLING,
            CoreState.STARTING,
            CoreState.RUNNING,
            CoreState.STOPPING,
        )

    companion object {
        fun from(snapshot: CoreSnapshot): CoreUiState = CoreUiState(
            state = snapshot.state,
            version = snapshot.version
                ?.split(" ")
                ?.take(2)
                ?.joinToString(" "),
            deviceName = snapshot.deviceName,
            uptimeSeconds = snapshot.uptimeSeconds,
            downloadBytesPerSecond = snapshot.downloadBytesPerSecond,
            uploadBytesPerSecond = snapshot.uploadBytesPerSecond,
            downloadedBytes = snapshot.downloadedBytes,
            uploadedBytes = snapshot.uploadedBytes,
            totalFileSizeBytes = snapshot.totalFileSizeBytes,
            rssBytes = snapshot.rssBytes,
            allocatedBytes = snapshot.allocatedBytes,
            systemBytes = snapshot.systemBytes,
            goroutines = snapshot.goroutines,
            lastError = snapshot.lastError,
            operationMessage = snapshot.operationMessage,
            selectedCoreId = snapshot.selectedCoreId,
            selectedCoreSource = snapshot.selectedCoreSource,
            availableCores = snapshot.availableCores,
            canSelectCore = snapshot.canSelectCore,
        )
    }
}
