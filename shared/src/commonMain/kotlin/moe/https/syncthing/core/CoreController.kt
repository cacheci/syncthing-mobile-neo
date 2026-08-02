package moe.https.syncthing.core

import kotlinx.coroutines.flow.StateFlow

interface CoreController {
    val snapshot: StateFlow<CoreSnapshot>

    fun start()

    fun stop()
}

interface DevicesController {
    suspend fun loadDevices(): DevicesSnapshot

    suspend fun addDevice(
        deviceId: String,
        name: String,
        addresses: List<String>,
    )
}
