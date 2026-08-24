package io.github.soclear.oneuix.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import io.github.soclear.oneuix.hook.systemui.SystemUiWakeBridge

class ScreenWakeController(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = context.getSystemService(PowerManager::class.java)

    val isInteractive: Boolean
        get() = powerManager.isInteractive

    @Suppress("DEPRECATION")
    fun wake(durationSeconds: Int): Boolean = try {
        val effectiveDurationSeconds = durationSeconds.coerceIn(1, 10)
        val timeoutMillis = effectiveDurationSeconds * 1_000L
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK,
            "OneUIX:NotificationWake"
        )
        wakeLock.acquire(timeoutMillis)
        appContext.sendBroadcast(
            Intent(SystemUiWakeBridge.ACTION_WAKE_SCREEN)
                .setPackage(SystemUiWakeBridge.SYSTEM_UI_PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(
                    SystemUiWakeBridge.EXTRA_DURATION_SECONDS,
                    effectiveDurationSeconds,
                )
        )
        true
    } catch (error: Throwable) {
        InteractionDiagnostics.log("Wake", "failed=${error.javaClass.simpleName}")
        false
    }
}
