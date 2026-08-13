package moe.https.syncthing.core

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

class AndroidCoreController(
    context: Context,
    runtime: CoreRuntime,
) : CoreController {
    private val applicationContext = context.applicationContext

    override val snapshot: StateFlow<CoreSnapshot> = runtime.snapshot

    override fun start() {
        val intent = Intent(applicationContext, SyncthingCoreService::class.java)
            .setAction(SyncthingCoreService.ACTION_START)
        applicationContext.startForegroundService(intent)
    }

    override fun stop() {
        val intent = Intent(applicationContext, SyncthingCoreService::class.java)
            .setAction(SyncthingCoreService.ACTION_STOP)
        applicationContext.startService(intent)
    }
}
