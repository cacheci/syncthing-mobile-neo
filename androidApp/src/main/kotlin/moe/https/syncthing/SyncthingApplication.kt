package moe.https.syncthing

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.https.syncthing.core.AndroidCoreController
import moe.https.syncthing.core.CoreBinaryInstaller
import moe.https.syncthing.core.CoreRuntime
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.storage.SharedPreferencesAppSettingsStorage

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
        val installer = CoreBinaryInstaller(this)
        coreRuntime = CoreRuntime(this, installer, appSettingsStorage)
        coreController = AndroidCoreController(this, coreRuntime)
        applicationScope.launch {
            coreRuntime.refreshInstallation()
        }
    }
}
