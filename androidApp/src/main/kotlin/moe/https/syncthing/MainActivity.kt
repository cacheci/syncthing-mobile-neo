package moe.https.syncthing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import moe.https.syncthing.core.AndroidCoreLogReader
import moe.https.syncthing.storage.ProtocolStack
import moe.https.syncthing.ui.model.CoreUiEffect
import moe.https.syncthing.viewmodel.LogViewModel
import moe.https.syncthing.viewmodel.CoreViewModel
import moe.https.syncthing.viewmodel.DevicesViewModel
import moe.https.syncthing.viewmodel.FoldersViewModel
import moe.https.syncthing.viewmodel.SettingViewModel

class MainActivity : ComponentActivity() {
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
        SettingViewModel.factory(applicationState.coreRuntime)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                mutableStateOf(storage.appDeveloperMode)
            }
            var protocolStack by remember {
                mutableStateOf(storage.appProtocolStack)
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
                    storage.appDeveloperMode = !developerModeEnabled
                    developerModeEnabled = storage.appDeveloperMode
                },
                protocolStack = protocolStack,
                onProtocolStackChange = { selectedStack: ProtocolStack ->
                    storage.appProtocolStack = selectedStack
                    protocolStack = storage.appProtocolStack
                },
                webUiUrlProvider = applicationState.coreRuntime::guiUrl,
                webView = { url, reloadToken, onScroll, modifier ->
                    AndroidSystemWebView(
                        url = url,
                        reloadToken = reloadToken,
                        onScroll = onScroll,
                        modifier = modifier,
                    )
                },
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
