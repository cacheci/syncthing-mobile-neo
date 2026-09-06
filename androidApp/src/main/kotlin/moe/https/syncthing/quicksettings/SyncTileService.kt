package moe.https.syncthing.quicksettings

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.https.syncthing.R
import moe.https.syncthing.SyncthingApplication
import moe.https.syncthing.core.CoreState

class SyncTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller
        get() = (application as SyncthingApplication).coreController

    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            controller.snapshot.collectLatest { snapshot ->
                updateTile(snapshot.state)
            }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (controller.snapshot.value.state) {
            CoreState.STOPPED -> {
                controller.start()
                updateTile(CoreState.STARTING)
            }

            CoreState.STARTING,
            CoreState.RUNNING,
            CoreState.FAILED,
            -> {
                controller.stop()
                updateTile(CoreState.STOPPING)
            }

            CoreState.NOT_INSTALLED,
            CoreState.INSTALLING,
            CoreState.STOPPING,
            -> Unit
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateTile(state: CoreState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.app_name)
        tile.state = when (state) {
            CoreState.STOPPED -> Tile.STATE_INACTIVE
            CoreState.STARTING,
            CoreState.RUNNING,
            CoreState.FAILED,
            -> Tile.STATE_ACTIVE

            CoreState.NOT_INSTALLED,
            CoreState.INSTALLING,
            CoreState.STOPPING,
            -> Tile.STATE_UNAVAILABLE
        }
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}
