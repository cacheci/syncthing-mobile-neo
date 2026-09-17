package moe.https.syncthing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.storage.NSUserDefaultsAppSettingsStorage
import moe.https.syncthing.viewmodel.BackupViewModel
import moe.https.syncthing.viewmodel.CoreViewModel
import moe.https.syncthing.viewmodel.DevicesViewModel
import moe.https.syncthing.viewmodel.FoldersViewModel
import moe.https.syncthing.viewmodel.LogViewModel
import moe.https.syncthing.viewmodel.MainViewModel
import moe.https.syncthing.viewmodel.RecentChangesViewModel
import moe.https.syncthing.viewmodel.SettingViewModel
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    IosApp()
}

@Composable
private fun IosApp() {
    val services = remember { IosUnavailablePlatformServices() }
    val storage = remember { NSUserDefaultsAppSettingsStorage() }
    val coreViewModel = remember { CoreViewModel(services) }
    val logViewModel = remember { LogViewModel(services) }
    val devicesViewModel = remember { DevicesViewModel(services) }
    val foldersViewModel = remember { FoldersViewModel(services) }
    val recentChangesViewModel = remember { RecentChangesViewModel(services) }
    val settingViewModel = remember { SettingViewModel(services, storage) }
    val mainViewModel = remember { MainViewModel(storage) }
    val backupViewModel = remember { BackupViewModel(services) }
    var developerModeEnabled by remember {
        mutableStateOf(
            storage.getBoolean(AppSettingPrivateStorage.KEY_DEVELOPER_MODE, false),
        )
    }

    App(
        coreViewModel = coreViewModel,
        logViewModel = logViewModel,
        devicesViewModel = devicesViewModel,
        foldersViewModel = foldersViewModel,
        recentChangesViewModel = recentChangesViewModel,
        settingViewModel = settingViewModel,
        mainViewModel = mainViewModel,
        backupViewModel = backupViewModel,
        versionName = appVersionName(),
        developerModeEnabled = developerModeEnabled,
        onModifyDeveloperMode = {
            developerModeEnabled = !developerModeEnabled
            storage.putBoolean(
                AppSettingPrivateStorage.KEY_DEVELOPER_MODE,
                developerModeEnabled,
            )
        },
        onScanQrCode = {},
        publicStorageAccessGranted = false,
        onRequestPublicStorageAccess = ::openApplicationSettings,
        currentWifiName = null,
        wifiNameAccessGranted = false,
        locationServiceEnabled = false,
        onRequestWifiNameAccess = ::openApplicationSettings,
        onOpenLocationSettings = ::openApplicationSettings,
        batteryOptimizationExempt = true,
        onBatteryOptimizationRequest = {},
        onOpenAppDetailsSettings = ::openApplicationSettings,
        scannedDeviceId = "",
        webUiUrlProvider = { "" },
        webView = { _, _, _, modifier ->
            Box(modifier = modifier.fillMaxSize())
        },
    )
}

private fun appVersionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: "1.0"

private fun openApplicationSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(
        url = url,
        options = emptyMap<Any?, Any>(),
        completionHandler = null,
    )
}
