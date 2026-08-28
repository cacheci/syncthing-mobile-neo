package moe.https.syncthing.ui.model

import moe.https.syncthing.core.FoldersSnapshot
import moe.https.syncthing.core.SyncthingFolder
import moe.https.syncthing.core.SyncthingPendingFolder

data class FoldersUiState(
    val folders: List<SyncthingFolder> = emptyList(),
    val pendingFolders: List<SyncthingPendingFolder> = emptyList(),
    val isLoading: Boolean = false,
    val isPendingFolderActionInProgress: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)

fun FoldersUiState.updateFrom(snapshot: FoldersSnapshot): FoldersUiState = copy(
    folders = snapshot.folders,
    pendingFolders = snapshot.pendingFolders,
    isLoading = false,
    isPendingFolderActionInProgress = false,
    hasLoaded = true,
    errorMessage = null,
)
