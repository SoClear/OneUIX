package io.github.soclear.oneuix

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ImmersiveModeTileService : TileService() {
    override fun onClick() {
        try {
            val policy = Settings.Global.getString(contentResolver, POLICY_CONTROL)
            Settings.Global.putString(
                contentResolver, POLICY_CONTROL, toggleStatusBarPolicy(policy)
            )
        } catch (_: Exception) {
            setTileState(Tile.STATE_UNAVAILABLE)
            return
        }
        updateTileState()
    }

    override fun onStartListening() {
        if (!canWriteSecureSettings() && !grantPermission()) {
            setTileState(Tile.STATE_UNAVAILABLE)
            return
        }
        updateTileState()
    }

    private fun updateTileState() {
        val policy = try {
            Settings.Global.getString(contentResolver, POLICY_CONTROL)
        } catch (_: Exception) {
            setTileState(Tile.STATE_UNAVAILABLE)
            return
        }
        setTileState(
            if (isImmersiveMode(policy)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        )
    }

    private fun canWriteSecureSettings(): Boolean {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun setTileState(state: Int) {
        qsTile.state = state
        qsTile.updateTile()
    }

    companion object {
        private const val POLICY_CONTROL = "policy_control"
        private const val POLICY_SEPARATOR = ":"
        private const val STATUS_BAR_POLICY = "immersive.status=*"

        private fun isImmersiveMode(policy: String?): Boolean {
            return parsePolicies(policy).contains(STATUS_BAR_POLICY)
        }

        private fun toggleStatusBarPolicy(policy: String?): String? {
            val policies = parsePolicies(policy)
            val updated = if (STATUS_BAR_POLICY in policies) {
                policies.filterNot { it == STATUS_BAR_POLICY }
            } else {
                policies + STATUS_BAR_POLICY
            }
            return updated.joinToString(POLICY_SEPARATOR).takeIf(String::isNotEmpty)
        }

        private fun parsePolicies(policy: String?): List<String> {
            return policy
                ?.split(POLICY_SEPARATOR)
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
        }

        private fun grantPermission(): Boolean = try {
            ProcessBuilder(
                "su",
                "-c",
                "pm",
                "grant",
                BuildConfig.APPLICATION_ID,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ).start().waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}