package io.github.soclear.oneuix

import android.app.Activity
import android.os.Bundle
import io.github.soclear.oneuix.common.PowerMenuAction

class RebootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.action?.let { action ->
            val command = if (action == PowerMenuAction.SOFT_REBOOT) {
                "setprop ctl.restart zygote"
            } else {
                "reboot $action"
            }
            ProcessBuilder("su", "-c", command).start()
        }
        finish()
    }
}
