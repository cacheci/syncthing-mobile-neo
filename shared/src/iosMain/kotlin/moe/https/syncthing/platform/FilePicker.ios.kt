package moe.https.syncthing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberPemFilePicker(
    onResult: (FilePickerResult) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        {
            // TODO: Present UIDocumentPickerViewController when iOS core configuration is enabled.
            currentOnResult.value(
                FilePickerResult.Error("iOS 文件导入将在 Syncthing 核心移植阶段启用"),
            )
        }
    }
}
