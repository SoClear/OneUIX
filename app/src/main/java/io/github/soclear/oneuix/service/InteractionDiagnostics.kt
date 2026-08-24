package io.github.soclear.oneuix.service

import android.content.Context
import android.util.Log
import androidx.core.content.edit

object InteractionDiagnostics {
    private const val TAG = "OneUIX.Interaction"
    private const val STORE = "interaction_diagnostics"
    private const val LAST_WAKE = "last_wake_reason"
    private const val LAST_AUTO_START = "last_auto_start_result"

    fun log(component: String, message: String) {
        Log.i(TAG, "[$component] $message")
    }

    fun recordWake(context: Context, reason: String) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit { putString(LAST_WAKE, reason) }
        log("Wake", reason)
    }

    fun recordPermissionStatus(context: Context, granted: Boolean) {
        log("Wake", "notificationListenerGranted=$granted")
    }

    fun lastWakeReason(context: Context): String = context
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getString(LAST_WAKE, null)
        ?.let { "Last wake: $it" }
        ?: "Last wake: none"

    fun recordAutoStart(context: Context, result: String) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit { putString(LAST_AUTO_START, result) }
        log("AutoStart", result)
    }

    fun lastAutoStartResult(context: Context): String = context
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getString(LAST_AUTO_START, null)
        ?.let { "Last auto-start: $it" }
        ?: "Last auto-start: none"
}
