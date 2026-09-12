package io.github.soclear.oneuix

import android.app.Activity
import android.os.Bundle

class RebootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.action?.let {
            ProcessBuilder("su", "-c", "reboot", it).start()
        }
        finish()
    }
}
