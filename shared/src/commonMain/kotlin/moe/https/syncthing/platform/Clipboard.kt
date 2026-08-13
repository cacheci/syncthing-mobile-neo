package moe.https.syncthing.platform

import androidx.compose.runtime.Composable

interface Clipboard {
    fun copy(text: String)
}

@Composable
expect fun rememberClipboard(): Clipboard
