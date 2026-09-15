package moe.https.syncthing.platform

import androidx.compose.runtime.Composable

sealed interface FilePickerResult {
    data class Selected(val content: ByteArray) : FilePickerResult

    data class Error(val message: String) : FilePickerResult

    data object Cancelled : FilePickerResult
}

@Composable
expect fun rememberPemFilePicker(
    onResult: (FilePickerResult) -> Unit,
): () -> Unit
