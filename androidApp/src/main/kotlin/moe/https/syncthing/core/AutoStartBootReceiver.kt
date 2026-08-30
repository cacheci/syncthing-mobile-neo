package moe.https.syncthing.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import moe.https.syncthing.SyncthingApplication
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.ui.util.AutoStartModeType

@RequiresApi(Build.VERSION_CODES.R)
class AutoStartBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val application = context.applicationContext as SyncthingApplication
        val mode = application.appSettingsStorage
            .getString(AppSettingPrivateStorage.KEY_AUTO_START_MODE)
            ?.let { stored -> AutoStartModeType.entries.firstOrNull { it.name == stored } }
            ?: AutoStartModeType.DISABLED
        if (mode == AutoStartModeType.DISABLED) return
        context.startForegroundService(
            Intent(context, SyncthingCoreService::class.java)
                .setAction(SyncthingCoreService.ACTION_REEVALUATE_AUTO_START),
        )
    }
}
