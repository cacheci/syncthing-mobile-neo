package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.https.syncthing.ui.model.DevicesUiState

class DevicesViewModel (

) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = mutableUiState.asStateFlow()

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DevicesViewModel()
            }
        }
    }
}
