package io.github.soclear.oneuix

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AudioEraserTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }
}
