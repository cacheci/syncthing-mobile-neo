package moe.https.syncthing

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.https.syncthing.core.AndroidCoreController
import moe.https.syncthing.core.BuiltInCoreProvider
import moe.https.syncthing.core.CoreRegistry
import moe.https.syncthing.core.CoreRuntime
import moe.https.syncthing.core.ExternalCoreInstaller
import moe.https.syncthing.core.SyncthingCoreService
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.storage.SharedPreferencesAppSettingsStorage
import moe.https.syncthing.ui.util.AutoStartModeType

class SyncthingApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var coreRuntime: CoreRuntime
        private set

    lateinit var coreController: AndroidCoreController
        private set

    lateinit var appSettingsStorage: AppSettingPrivateStorage
        private set

    override fun onCreate() {
        super.onCreate()
        appSettingsStorage = SharedPreferencesAppSettingsStorage(this)
        val coreRegistry = CoreRegistry(
            context = this,
            builtInProvider = BuiltInCoreProvider(this),
            externalInstaller = ExternalCoreInstaller(this),
        )
        coreRuntime = CoreRuntime(this, coreRegistry, appSettingsStorage)
        coreController = AndroidCoreController(this, coreRuntime)
        applicationScope.launch {
            coreRuntime.refreshInstallation()
        }
    }

    fun onAutoStartSettingsChanged() {
        val intent = Intent(this, SyncthingCoreService::class.java)
            .setAction(SyncthingCoreService.ACTION_REEVALUATE_AUTO_START)
        val mode = appSettingsStorage.getString(AppSettingPrivateStorage.KEY_AUTO_START_MODE)
            ?.let { stored -> AutoStartModeType.entries.firstOrNull { it.name == stored } }
            ?: AutoStartModeType.DISABLED
        if (mode == AutoStartModeType.DISABLED) {
            startService(intent)
        } else {
            startForegroundService(intent)
        }
    }
}
