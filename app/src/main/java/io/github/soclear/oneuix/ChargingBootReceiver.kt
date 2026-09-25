package io.github.soclear.oneuix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.soclear.oneuix.ui.charging.ChargingControlRepository

class ChargingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                ChargingControlRepository(context.applicationContext).restoreAfterBoot()
            } catch (t: Throwable) {
                Log.e("OneUIX ChargingControl", "Could not restore charging limit after boot", t)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
