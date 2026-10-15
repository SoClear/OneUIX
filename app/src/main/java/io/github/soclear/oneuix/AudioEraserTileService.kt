package io.github.soclear.oneuix

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AudioEraserTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, AudioEraserTileService::class.java)
            val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (context.packageManager.getComponentEnabledSetting(component) != state) {
                context.packageManager.setComponentEnabledSetting(
                    component, state, PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
