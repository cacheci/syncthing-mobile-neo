package moe.https.syncthing.platform

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun isSystem24HourFormat(): Boolean =
    DateFormat.is24HourFormat(LocalContext.current)
