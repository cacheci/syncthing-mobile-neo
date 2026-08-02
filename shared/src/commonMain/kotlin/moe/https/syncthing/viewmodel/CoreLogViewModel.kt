package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.https.syncthing.core.CoreLogReader
import moe.https.syncthing.core.CoreLogSource
import moe.https.syncthing.ui.model.CoreLogUiState

class CoreLogViewModel(
    private val reader: CoreLogReader,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CoreLogUiState())
    val uiState: StateFlow<CoreLogUiState> = mutableUiState.asStateFlow()

    private val readMutex = Mutex()
    private var pollingJob: Job? = null

    fun onSourceSelected(source: CoreLogSource) {
        if (mutableUiState.value.source == source) return
        mutableUiState.update {
            it.copy(
                source = source,
                content = "",
                refreshedAt = null,
                error = null,
            )
        }
        refresh()
    }

    fun onPageVisibilityChanged(visible: Boolean) {
        if (!visible) {
            pollingJob?.cancel()
            pollingJob = null
            return
        }
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                load()
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() = readMutex.withLock {
        val source = mutableUiState.value.source
        mutableUiState.update { it.copy(isLoading = true, error = null) }
        runCatching { reader.read(source) }
            .onSuccess { result ->
                if (mutableUiState.value.source == source) {
                    mutableUiState.update {
                        it.copy(
                            content = result.text,
                            refreshedAt = result.refreshedAt,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
            }
            .onFailure { error ->
                if (mutableUiState.value.source == source) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: error::class.simpleName ?: "读取日志失败",
                        )
                    }
                }
            }
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 2_000L

        fun factory(reader: CoreLogReader): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CoreLogViewModel(reader)
            }
        }
    }
}
