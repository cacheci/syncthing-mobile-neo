package moe.https.syncthing.ui.model

import moe.https.syncthing.core.SyncthingRecentChange

data class RecentChangesUiState(
    val changes: List<SyncthingRecentChange> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)
