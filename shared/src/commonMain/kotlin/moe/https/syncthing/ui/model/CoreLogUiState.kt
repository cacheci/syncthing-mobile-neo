package moe.https.syncthing.ui.model

import moe.https.syncthing.core.CoreLogSource

data class CoreLogUiState(
    val source: CoreLogSource = CoreLogSource.SYNCTHING,
    val content: String = "",
    val refreshedAt: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
