package io.github.soclear.oneuix

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Supplies an ordinary, editable QS tile. The SystemUI hook connects it directly to Samsung's
 * controller; no exported command receiver, root shell or app/package allowlist is needed.
 * Without the hook it deliberately stays unavailable rather than presenting a working toggle.
 */
class AudioEraserTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            label = getString(R.string.audioEraserQsTile_label)
            updateTile()
        }
    }
}
