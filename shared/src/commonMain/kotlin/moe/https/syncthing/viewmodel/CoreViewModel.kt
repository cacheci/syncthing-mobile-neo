package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.https.syncthing.core.CoreController
import moe.https.syncthing.ui.model.CoreUiEffect
import moe.https.syncthing.ui.model.CoreUiState

class CoreViewModel(
    private val controller: CoreController,
) : ViewModel() {
    val uiState: StateFlow<CoreUiState> = controller.snapshot
        .map(CoreUiState::from)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CoreUiState.from(controller.snapshot.value),
        )

    private val mutableEffects = MutableSharedFlow<CoreUiEffect>(
        extraBufferCapacity = 1,
    )
    val effects = mutableEffects.asSharedFlow()

    fun onStartClicked() {
        controller.start()
    }

    fun onStopClicked() {
        controller.stop()
    }

    fun onImportCoreClicked() {
        mutableEffects.tryEmit(CoreUiEffect.OpenCorePicker)
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(controller: CoreController): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CoreViewModel(controller)
            }
        }
    }
}
