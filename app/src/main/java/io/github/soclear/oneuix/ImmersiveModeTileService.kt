package io.github.soclear.oneuix

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ImmersiveModeTileService : TileService() {
    override fun onClick() {
        val policy = readPolicyControl()
        if (isImmersiveMode(policy)) {
            updatePolicyControl(policy.orEmpty().split(',').filterNot { it == STATUS_BAR_POLICY })
        } else {
            updatePolicyControl(policy.orEmpty().split(',').filter { it.isNotEmpty() } + STATUS_BAR_POLICY)
        }
        updateTile()
    }

    override fun onStartListening() {
        updateTile()
    }

    private fun updateTile() {
        qsTile.state = if (isImmersiveMode(readPolicyControl())) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }

    private fun readPolicyControl(): String? = runAsRoot("settings get global policy_control")
        ?.trim()
        ?.takeUnless { it.isEmpty() || it == "null" }

    private fun updatePolicyControl(policies: List<String>) {
        val policy = policies.filter { it.isNotEmpty() }.joinToString(",")
        runAsRoot(setPolicyControlCommand(policies))
    }

    private fun runAsRoot(command: String): String? = try {
        ProcessBuilder("su", "-c", command).start().run {
            val output = inputStream.bufferedReader().readText()
            waitFor()
            output.takeIf { exitValue() == 0 }
        }
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val STATUS_BAR_POLICY = "immersive.status=*"

        fun isImmersiveMode(policy: String?): Boolean {
            return policy?.split(',')?.any { it == STATUS_BAR_POLICY } == true
        }

        fun setPolicyControlCommand(policies: List<String>): String {
            val policy = policies.filter { it.isNotEmpty() }.joinToString(",")
            return if (policy.isEmpty()) {
                "settings delete global policy_control"
            } else {
                "settings put global policy_control '$policy'"
            }
        }
    }
}
