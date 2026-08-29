package moe.https.syncthing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.https.syncthing.core.AndroidCoreLogReader
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.ui.model.CoreUiEffect
import moe.https.syncthing.viewmodel.CoreViewModel
import moe.https.syncthing.viewmodel.DevicesViewModel
import moe.https.syncthing.viewmodel.FoldersViewModel
import moe.https.syncthing.viewmodel.LogViewModel
import moe.https.syncthing.viewmodel.SettingViewModel
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private var scannedDeviceId by mutableStateOf("")
    private var publicStorageAccessGranted by mutableStateOf(false)
    private var batteryOptimizationExempt by mutableStateOf(false)
    private var waitingBatteryOptimizationResult = false

    private val scanQrCodeLauncher = registerForActivityResult(ScanQRCode()) { result ->
        if (result is QRResult.QRSuccess) {
            scannedDeviceId = result.content.rawValue.orEmpty()
        }
    }

    private val applicationState: SyncthingApplication
        get() = application as SyncthingApplication

    private val coreViewModel: CoreViewModel by viewModels {
        CoreViewModel.factory(applicationState.coreController)
    }

    private val logViewModel: LogViewModel by viewModels {
        LogViewModel.factory(AndroidCoreLogReader(applicationContext))
    }

    private val devicesViewModel: DevicesViewModel by viewModels {
        DevicesViewModel.factory(applicationState.coreRuntime)
    }

    private val foldersViewModel: FoldersViewModel by viewModels {
        FoldersViewModel.factory(applicationState.coreRuntime)
    }

    private val settingViewModel: SettingViewModel by viewModels {
        SettingViewModel.factory(
            applicationState.coreRuntime,
            appSettingsStorage = applicationState.appSettingsStorage,
        )
    }

    private val corePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                applicationState.coreRuntime.importCore(uri)
            }
        }
    }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        publicStorageAccessGranted = hasPublicStorageAccess()
    }

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        publicStorageAccessGranted = hasPublicStorageAccess()
    }

    private val backgroundSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publicStorageAccessGranted = hasPublicStorageAccess()
        batteryOptimizationExempt = isBatteryOptimizationExempt()
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                coreViewModel.effects.collect { effect ->
                    when (effect) {
                        CoreUiEffect.OpenCorePicker -> openCorePicker()
                    }
                }
            }
        }
        setContent {
            val storage = applicationState.appSettingsStorage
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
                settingViewModel = settingViewModel,
                versionName = BuildConfig.VERSION_NAME,
                developerModeEnabled = developerModeEnabled,
                onModifyDeveloperMode = {
                    val newValue = !developerModeEnabled
                    storage.putBoolean(AppSettingPrivateStorage.KEY_DEVELOPER_MODE, newValue)
                    developerModeEnabled = newValue
                },
                onScanQrCode = {
                    scanQrCodeLauncher.launch(null)
                },
                publicStorageAccessGranted = publicStorageAccessGranted,
                onRequestPublicStorageAccess = ::requestPublicStorageAccess,
                batteryOptimizationExempt = batteryOptimizationExempt,
                onBatteryOptimizationRequest = ::batteryOptimizationExemption,
                onOpenAppDetailsSettings = ::openAppDetailsSettings,
                scannedDeviceId = scannedDeviceId,
                webUiUrlProvider = applicationState.coreRuntime::guiUrl,
                webView = { url, reloadToken, onScroll, modifier ->
                    val credentials = applicationState.coreRuntime.guiCredentials()
                    AndroidSystemWebView(
                        url = url,
                        username = credentials?.first.orEmpty(),
                        password = credentials?.second.orEmpty(),
                        reloadToken = reloadToken,
                        onScroll = onScroll,
                        modifier = modifier,
                    )
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        publicStorageAccessGranted = hasPublicStorageAccess()
        batteryOptimizationExempt = isBatteryOptimizationExempt()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !waitingBatteryOptimizationResult) return

        waitingBatteryOptimizationResult = false
        lifecycleScope.launch {
            delay(500.milliseconds)
            batteryOptimizationExempt = isBatteryOptimizationExempt()
            delay(500.milliseconds)
            batteryOptimizationExempt = isBatteryOptimizationExempt()
        }
    }

    private fun isBatteryOptimizationExempt(): Boolean =
        getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife")
    private fun batteryOptimizationExemption() {
        waitingBatteryOptimizationResult = true
        backgroundSettingsLauncher.launch(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
            ),
        )
    }

    private fun openAppDetailsSettings() {
        backgroundSettingsLauncher.launch(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$packageName".toUri(),
            ),
        )
    }

    private fun hasPublicStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun requestPublicStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appSettingsIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:$packageName".toUri(),
            )
            val intent = if (appSettingsIntent.resolveActivity(packageManager) != null) {
                appSettingsIntent
            } else {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            allFilesAccessLauncher.launch(intent)
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    private fun openCorePicker() {
        corePicker.launch(
            arrayOf(
                "application/octet-stream",
                "application/x-executable",
                "application/x-sharedlib",
            ),
        )
    }
}
