package io.github.soclear.oneuix.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.ChargingControlKeys
import io.github.soclear.oneuix.common.ChargingControlPolicy
import io.github.soclear.oneuix.hook.util.getSystemContext
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File

object ChargingControl {
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun install() {
        try {
            val batteryService = param.classLoader.loadClass("com.android.server.BatteryService")
            val onStart = batteryService.getDeclaredMethod("onStart")
            xposedModule.hook(onStart).intercept { chain ->
                val result = chain.proceed()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        start(getSystemContext())
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                }, 5_000)
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    private fun start(context: Context) {
        val resolver = context.contentResolver
        val handler = Handler(Looper.getMainLooper())

        if (Settings.Global.getInt(resolver, ChargingControlKeys.REMEMBER, 0) != 1 &&
            Settings.Global.getInt(resolver, ChargingControlKeys.ACTIVE, 0) == 1
        ) {
            Settings.Global.putInt(resolver, ChargingControlKeys.ACTIVE, 0)
        }

        val evaluate = Runnable { applyLimit(context) }
        val schedule = {
            handler.removeCallbacks(evaluate)
            handler.postDelayed(evaluate, 150)
        }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { schedule() }
        }
        listOf(
            ChargingControlKeys.LIMIT,
            ChargingControlKeys.ACTIVE,
            ChargingControlKeys.REMEMBER,
        ).forEach {
            resolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer)
        }
        resolver.registerContentObserver(
            Settings.System.getUriFor(ChargingControlKeys.BYPASS), false, observer
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { schedule() }
        }, filter)
        val keepAlive = object : Runnable {
            override fun run() {
                applyLimit(context)
                handler.postDelayed(this, 15_000)
            }
        }
        handler.postDelayed(keepAlive, 15_000)
        schedule()
    }

    private fun applyLimit(context: Context) {
        try {
            val resolver = context.contentResolver
            val active = Settings.Global.getInt(resolver, ChargingControlKeys.ACTIVE, 0) == 1
            val limit = Settings.Global.getInt(resolver, ChargingControlKeys.LIMIT, -1)
            val battery = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percentage = if (scale > 0) level * 100 / scale else -1
            val pluggedIn = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val shouldPause = active && limit in 20..100 &&
                ChargingControlPolicy.shouldPause(percentage, limit, pluggedIn)
            val owned = Settings.Global.getInt(resolver, ChargingControlKeys.OWNED_BYPASS, 0) == 1
            val current = Settings.System.getInt(resolver, ChargingControlKeys.BYPASS, 0)
            if (active && limit in 20..100) {
                try {
                    val threshold = File(ChargingControlKeys.KERNEL_THRESHOLD)
                    if (threshold.readText().trim().toIntOrNull() != limit) {
                        threshold.writeText(limit.toString())
                    }
                } catch (t: Throwable) {
                    Log.e("OneUIX ChargingControl", "Could not restore kernel charge limit", t)
                }
            }

            if (shouldPause && owned) {
                if (current != 1) Settings.System.putInt(resolver, ChargingControlKeys.BYPASS, 1)
            } else if (owned && !shouldPause) {
                if (current != 0) Settings.System.putInt(resolver, ChargingControlKeys.BYPASS, 0)
            }
        } catch (t: Throwable) {
            Log.e("OneUIX ChargingControl", "Could not apply charging limit", t)
        }
    }
}
