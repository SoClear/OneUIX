package io.github.soclear.oneuix

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AutoBrightnessTileService : TileService() {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = updateTile()
    }

    override fun onStartListening() {
        if (!ensurePermission()) {
            updateTile(Tile.STATE_UNAVAILABLE)
            return
        }
        contentResolver.registerContentObserver(Settings.System.getUriFor(SCREEN_BRIGHTNESS_MODE), false, observer)
        updateTile()
    }

    override fun onStopListening() = contentResolver.unregisterContentObserver(observer)

    override fun onClick() {
        val mode = if (isEnabled()) SCREEN_BRIGHTNESS_MODE_MANUAL else SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        if (!runCatching {
            Settings.System.putInt(contentResolver, SCREEN_BRIGHTNESS_MODE, mode)
        }.getOrDefault(false)) {
            updateTile(Tile.STATE_UNAVAILABLE)
            return
        }
        updateTile()
    }

    private fun ensurePermission(): Boolean =
        checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED ||
            runCatching {
                ProcessBuilder(
                    "su", "-c", "pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"
                ).start().waitFor() == 0
            }.getOrDefault(false)

    private fun isEnabled() = Settings.System.getInt(
        contentResolver, SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL
    ) == SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun updateTile(state: Int = if (isEnabled()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE) {
        qsTile?.apply {
            this.state = state
            updateTile()
        }
    }
}
