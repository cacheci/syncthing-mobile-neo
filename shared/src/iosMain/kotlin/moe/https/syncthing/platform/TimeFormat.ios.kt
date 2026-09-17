package moe.https.syncthing.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

@Composable
actual fun isSystem24HourFormat(): Boolean {
    val format = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)
    return format?.contains("a") == false
}
