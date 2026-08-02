package moe.https.syncthing.ui.model

import moe.https.syncthing.core.DevicesSnapshot
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingLocalInfo

data class DevicesUiState(
    val devices: List<SyncthingDevice> = emptyList(),
    val localInfo: SyncthingLocalInfo? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)

fun DevicesUiState.updateFrom(snapshot: DevicesSnapshot): DevicesUiState = copy(
    devices = snapshot.devices,
    localInfo = snapshot.localInfo,
    isLoading = false,
    hasLoaded = true,
    errorMessage = null,
)
