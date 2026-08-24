package moe.https.syncthing.platform

import androidx.compose.runtime.Composable

sealed interface FolderPickerResult {
    data class Selected(val path: String) : FolderPickerResult

    data class Error(val message: String) : FolderPickerResult

    data object Cancelled : FolderPickerResult
}

@Composable
expect fun rememberFolderPicker(
    onResult: (FolderPickerResult) -> Unit,
): () -> Unit
