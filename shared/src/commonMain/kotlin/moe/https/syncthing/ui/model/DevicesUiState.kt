package moe.https.syncthing.ui.model

import moe.https.syncthing.core.DevicesSnapshot
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingLocalInfo
import moe.https.syncthing.core.SyncthingPendingDevice

data class DevicesUiState(
    val devices: List<SyncthingDevice> = emptyList(),
    val pendingDevices: List<SyncthingPendingDevice> = emptyList(),
    val localInfo: SyncthingLocalInfo? = null,
    val isLoading: Boolean = false,
    val isPendingDeviceActionInProgress: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)

fun DevicesUiState.updateFrom(snapshot: DevicesSnapshot): DevicesUiState = copy(
    devices = snapshot.devices,
    pendingDevices = snapshot.pendingDevices,
    localInfo = snapshot.localInfo,
    isLoading = false,
    isPendingDeviceActionInProgress = false,
    hasLoaded = true,
    errorMessage = null,
)
