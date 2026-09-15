package moe.https.syncthing.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.IOException

@Composable
actual fun rememberPemFilePicker(
    onResult: (FilePickerResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            currentOnResult(FilePickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        val result = runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    if (totalBytes > MAX_PEM_FILE_BYTES) {
                        throw IOException("所选 PEM 文件不能超过 1 MiB")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: throw IOException("无法读取所选文件")
            FilePickerResult.Selected(content)
        }.getOrElse { error ->
            FilePickerResult.Error(error.message ?: "无法读取所选文件")
        }
        currentOnResult(result)
    }

    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    "application/x-pem-file",
                    "application/pkix-cert",
                    "application/octet-stream",
                    "text/plain",
                ),
            )
        }
    }
}

private const val MAX_PEM_FILE_BYTES = 1024 * 1024
