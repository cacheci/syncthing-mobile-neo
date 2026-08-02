package moe.https.syncthing.ui.model

import moe.https.syncthing.core.SyncthingDevice

data class DevicesUiState(
    val devices: List<SyncthingDevice> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)
