package io.github.soclear.oneuix.hook.systemui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SystemUiWakeBridge {
    const val ACTION_WAKE_SCREEN = "io.github.soclear.oneuix.action.WAKE_SCREEN"
    const val EXTRA_DURATION_SECONDS = "duration_seconds"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val SYSTEM_UI_APPLICATION =
        "com.android.systemui.application.impl.SystemUIApplicationImpl"
    private const val WAKE_REASON_APPLICATION = 2
    private const val GO_TO_SLEEP_REASON_TIMEOUT = 2
    private const val WAKE_DETAILS = "OneUIX:NotificationWake"
    private const val SENDER_PERMISSION =
        BuildConfig.APPLICATION_ID + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    private val registered = AtomicBoolean(false)
    private val wakeGeneration = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enable(lpparam: LoadPackageParam) {
        if (lpparam.processName != SYSTEM_UI_PACKAGE) return
        try {
            val applicationClass =
                findClassIfExists(SYSTEM_UI_APPLICATION, lpparam.classLoader) ?: run {
                    InteractionHookLog.info(
                        "WakeBridge",
                        "SystemUIApplicationImpl not found; wake bridge unavailable",
                    )
                    return
                }
            findAndHookMethod(
                applicationClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!registered.compareAndSet(false, true)) return
                        val context = param.thisObject as? Context ?: return
                        try {
                            context.registerReceiver(
                                WakeReceiver(),
                                IntentFilter(ACTION_WAKE_SCREEN),
                                SENDER_PERMISSION,
                                null,
                                Context.RECEIVER_EXPORTED,
                            )
                            InteractionHookLog.info(
                                "WakeBridge",
                                "SystemUI receiver registered with signature sender permission",
                            )
                        } catch (error: Throwable) {
                            registered.set(false)
                            InteractionHookLog.failure("WakeBridge", error)
                        }
                    }
                },
            )
        } catch (error: Throwable) {
            InteractionHookLog.failure("WakeBridge", error)
        }
    }

    private class WakeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_WAKE_SCREEN) return
            try {
                val powerManager = context.getSystemService(PowerManager::class.java)
                val now = SystemClock.uptimeMillis()
                val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 8)
                    .coerceIn(1, 10)
                val generation = wakeGeneration.incrementAndGet()
                runCatching {
                    callMethod(
                        powerManager,
                        "wakeUp",
                        now,
                        WAKE_REASON_APPLICATION,
                        WAKE_DETAILS,
                    )
                }.getOrElse {
                    callMethod(powerManager, "wakeUp", now, WAKE_DETAILS)
                }
                mainHandler.postDelayed(
                    {
                        sleepIfStillOnKeyguard(context, powerManager, generation)
                    },
                    durationSeconds * 1_000L,
                )
                InteractionHookLog.info(
                    "WakeBridge",
                    "screen wake requested by SystemUI duration=${durationSeconds}s",
                )
            } catch (error: Throwable) {
                InteractionHookLog.failure("WakeBridge", error)
            }
        }
    }

    private fun sleepIfStillOnKeyguard(
        context: Context,
        powerManager: PowerManager,
        generation: Long,
    ) {
        if (wakeGeneration.get() != generation || !powerManager.isInteractive) return
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (!keyguardManager.isKeyguardLocked) {
            InteractionHookLog.info(
                "WakeBridge",
                "wake timeout retained because keyguard was dismissed",
            )
            return
        }
        try {
            val now = SystemClock.uptimeMillis()
            runCatching {
                callMethod(
                    powerManager,
                    "goToSleep",
                    now,
                    GO_TO_SLEEP_REASON_TIMEOUT,
                    0,
                )
            }.getOrElse {
                callMethod(powerManager, "goToSleep", now)
            }
            InteractionHookLog.info("WakeBridge", "wake timeout reached -> screen off")
        } catch (error: Throwable) {
            InteractionHookLog.failure("WakeBridgeSleep", error)
        }
    }
}
