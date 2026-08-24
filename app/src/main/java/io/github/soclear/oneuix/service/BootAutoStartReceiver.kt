package io.github.soclear.oneuix.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootAutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        InteractionDiagnostics.log("AutoStart", "boot completed -> schedule")
        AutoStartJobService.schedule(context, AutoStartJobService.REASON_SYSTEM_BOOT)
    }
}
