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
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SystemUiWakeBridge {
    const val ACTION_WAKE_SCREEN = "io.github.soclear.oneuix.action.WAKE_SCREEN"
    const val EXTRA_DURATION_SECONDS = "duration_seconds"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val SYSTEM_UI_APPLICATION =
        "com.android.systemui.application.impl.SystemUIApplicationImpl"
    private const val FACE_AUTH_INTERACTOR =
        "com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor"
    private const val FACE_AUTH_UI_EVENT =
        "com.android.systemui.deviceentry.shared.FaceAuthUiEvent"
    private const val FACE_AUTH_WAKE_EVENT = "FACE_AUTH_UPDATED_STARTED_WAKING_UP"
    private const val WAKE_REASON_GESTURE = 4
    private const val GO_TO_SLEEP_REASON_TIMEOUT = 2
    private const val FACE_AUTH_REQUEST_DELAY_MS = 300L
    private const val WAKE_DETAILS = "OneUIX:NotificationWake"
    private const val SENDER_PERMISSION =
        BuildConfig.APPLICATION_ID + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    private val registered = AtomicBoolean(false)
    private val faceAuthCaptureInstalled = AtomicBoolean(false)
    private val wakeGeneration = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var faceAuthInteractor: WeakReference<Any>? = null

    fun enable(lpparam: LoadPackageParam) {
        if (lpparam.processName != SYSTEM_UI_PACKAGE) return
        try {
            installFaceAuthCapture(lpparam.classLoader)
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
                        WAKE_REASON_GESTURE,
                        WAKE_DETAILS,
                    )
                }.getOrElse {
                    callMethod(powerManager, "wakeUp", now, WAKE_DETAILS)
                }
                mainHandler.postDelayed(
                    { requestNativeFaceAuth(context) },
                    FACE_AUTH_REQUEST_DELAY_MS,
                )
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

    private fun installFaceAuthCapture(classLoader: ClassLoader) {
        if (!faceAuthCaptureInstalled.compareAndSet(false, true)) return
        val interactorClass = findClassIfExists(FACE_AUTH_INTERACTOR, classLoader)
        if (interactorClass == null) {
            faceAuthCaptureInstalled.set(false)
            InteractionHookLog.info(
                "WakeFaceAuth",
                "native face-auth interactor unavailable",
            )
            return
        }
        hookAllConstructors(
            interactorClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    faceAuthInteractor = WeakReference(param.thisObject)
                    InteractionHookLog.info(
                        "WakeFaceAuth",
                        "native face-auth interactor captured",
                    )
                }
            },
        )
    }

    private fun requestNativeFaceAuth(context: Context) {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (!keyguardManager.isKeyguardLocked) return
        val interactor = faceAuthInteractor?.get() ?: run {
            InteractionHookLog.info(
                "WakeFaceAuth",
                "face-auth request skipped: interactor not ready",
            )
            return
        }
        try {
            val enabledAndEnrolled = callMethod(
                interactor,
                "isFaceAuthEnabledAndEnrolled",
            ) as? Boolean ?: false
            if (!enabledAndEnrolled) {
                InteractionHookLog.info(
                    "WakeFaceAuth",
                    "face-auth request skipped: not enabled or enrolled",
                )
                return
            }
            val isRunning = callMethod(interactor, "isAuthRunning") as? Boolean ?: false
            if (isRunning) {
                InteractionHookLog.info(
                    "WakeFaceAuth",
                    "native wake pipeline already started face authentication",
                )
                return
            }
            val eventClass = findClassIfExists(
                FACE_AUTH_UI_EVENT,
                interactor.javaClass.classLoader,
            ) ?: error("FaceAuthUiEvent unavailable")
            val event = getStaticObjectField(eventClass, FACE_AUTH_WAKE_EVENT)
            callMethod(event, "setExtraInfo", WAKE_REASON_GESTURE)
            callMethod(interactor, "runFaceAuth", event, true)
            InteractionHookLog.info(
                "WakeFaceAuth",
                "native face-auth request sent after notification wake",
            )
        } catch (error: Throwable) {
            InteractionHookLog.failure("WakeFaceAuth", error)
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
