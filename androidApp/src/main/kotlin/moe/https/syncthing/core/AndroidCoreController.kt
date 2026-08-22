package moe.https.syncthing.core

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

class AndroidCoreController(
    context: Context,
    private val runtime: CoreRuntime,
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

    override suspend fun selectCore(id: String) {
        runtime.selectCore(id)
    }

    override suspend fun deleteCore(id: String) {
        runtime.deleteCore(id)
    }
}
