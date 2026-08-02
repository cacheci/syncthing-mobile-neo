package moe.https.syncthing.core

import kotlinx.coroutines.flow.StateFlow

interface CoreController {
    val snapshot: StateFlow<CoreSnapshot>

    fun start()

    fun stop()
}
