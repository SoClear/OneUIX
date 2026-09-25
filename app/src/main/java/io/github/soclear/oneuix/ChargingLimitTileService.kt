package io.github.soclear.oneuix

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.soclear.oneuix.ui.charging.ChargingControlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChargingLimitTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy { ChargingControlRepository(applicationContext) }

    override fun onStartListening() {
        scope.launch { updateTileState() }
    }

    override fun onClick() {
        scope.launch {
            val applied = withContext(Dispatchers.IO) { repository.toggleQuickPause() }
            updateTileState()
            if (!applied) {
                Toast.makeText(
                    this@ChargingLimitTileService,
                    R.string.bypass_charging_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun updateTileState() {
        val tile = qsTile ?: return
        val (state, applied) = withContext(Dispatchers.IO) {
            val state = repository.read()
            state to repository.isQuickPauseApplied(state)
        }
        val level = state.batteryLevel
        tile.state = when {
            applied -> Tile.STATE_ACTIVE
            !state.pluggedIn || level == null || level < 20 -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = if (applied) getString(R.string.bypass_charging_on)
        else getString(R.string.charging_limit_value, state.limit)
        tile.updateTile()
    }
}
