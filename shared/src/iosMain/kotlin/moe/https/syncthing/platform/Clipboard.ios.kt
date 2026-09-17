package moe.https.syncthing.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboard(): Clipboard = remember {
    object : Clipboard {
        override fun copy(text: String) {
            UIPasteboard.generalPasteboard.string = text
        }
    }
}
