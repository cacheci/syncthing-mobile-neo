package moe.https.syncthing.ui.model

import moe.https.syncthing.core.FoldersSnapshot
import moe.https.syncthing.core.SyncthingFolder

data class FoldersUiState(
    val folders: List<SyncthingFolder> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)

fun FoldersUiState.updateFrom(snapshot: FoldersSnapshot): FoldersUiState = copy(
    folders = snapshot.folders,
    isLoading = false,
    hasLoaded = true,
    errorMessage = null,
)
