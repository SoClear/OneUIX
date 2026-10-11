package io.github.soclear.oneuix

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OutdoorModeTileService : TileService() {
    override fun onStartListening() = updateTile()

    override fun onClick() {
        val target = if (isEnabled()) 0 else 1
        runCatching {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(
                    contentResolver,
                    OUTDOOR_MODE,
                    target
                )
            }
        }
        if (isEnabled() != (target != 0)) {
            runCatching {
                ProcessBuilder(
                    "su",
                    "-c",
                    "settings put system $OUTDOOR_MODE $target"
                ).start().waitFor()
            }
        }
        updateTile()
    }

    private fun isEnabled(): Boolean = Settings.System.getInt(contentResolver, OUTDOOR_MODE, 0) != 0

    private fun updateTile() {
        qsTile?.apply {
            state = if (isEnabled()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        const val OUTDOOR_MODE = "display_outdoor_mode"
    }
}
