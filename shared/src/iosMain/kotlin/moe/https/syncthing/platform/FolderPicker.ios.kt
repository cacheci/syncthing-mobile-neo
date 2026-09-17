package moe.https.syncthing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberFolderPicker(
    onResult: (FolderPickerResult) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        {
            // TODO: Persist a security-scoped bookmark when iOS folder synchronization is enabled.
            currentOnResult.value(
                FolderPickerResult.Error("iOS 文件夹访问将在 Syncthing 核心移植阶段启用"),
            )
        }
    }
}
