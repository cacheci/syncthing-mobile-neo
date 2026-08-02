package moe.https.syncthing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import moe.https.syncthing.core.AndroidCoreLogReader
import moe.https.syncthing.ui.model.CoreUiEffect
import moe.https.syncthing.viewmodel.CoreLogViewModel
import moe.https.syncthing.viewmodel.CoreViewModel

class MainActivity : ComponentActivity() {
    private val applicationState: SyncthingApplication
        get() = application as SyncthingApplication

    private val coreViewModel: CoreViewModel by viewModels {
        CoreViewModel.factory(applicationState.coreController)
    }

    private val logViewModel: CoreLogViewModel by viewModels {
        CoreLogViewModel.factory(AndroidCoreLogReader(applicationContext))
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
            App(
                coreViewModel = coreViewModel,
                logViewModel = logViewModel,
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
