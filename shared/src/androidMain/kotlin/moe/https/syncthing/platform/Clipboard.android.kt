package moe.https.syncthing.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private class AndroidClipboard(
    private val clipboardManager: ClipboardManager,
) : Clipboard {
    override fun copy(text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
    }
}

@Composable
actual fun rememberClipboard(): Clipboard {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return AndroidClipboard(clipboardManager)
}
