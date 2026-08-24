package io.github.soclear.oneuix.hook.systemui

import android.app.KeyguardManager
import android.os.SystemClock
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.util.concurrent.atomic.AtomicLong

object NotificationQuickLaunch {
    private const val DIRECT_OPEN_WINDOW_MS = 3_000L
    private val directOpenUntil = AtomicLong(0)

    fun enable(lpparam: LoadPackageParam) {
        try {
            InteractionHookLog.environment("NotifyQuickLaunch", lpparam)
            val notificationClicker = findClassIfExists(
                "com.android.systemui.statusbar.notification.NotificationClicker",
                lpparam.classLoader
            ) ?: return logMissing("NotificationClicker")
            val starterClass = findClassIfExists(
                "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter",
                lpparam.classLoader
            ) ?: return logMissing("StatusBarNotificationActivityStarter")
            val keyguardViewManagerClass = findClassIfExists(
                "com.android.systemui.statusbar.phone.SecStatusBarKeyguardViewManager",
                lpparam.classLoader,
            ) ?: return logMissing("SecStatusBarKeyguardViewManager")
            val onDismissActionClass = findClassIfExists(
                "com.android.systemui.plugins.ActivityStarter\$OnDismissAction",
                lpparam.classLoader,
            ) ?: return logMissing("ActivityStarter.OnDismissAction")

            findAndHookMethod(
                notificationClicker,
                "onClick",
                View::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val result = evaluateEligibility(param.thisObject, param.args.firstOrNull())
                        if (result.eligible) {
                            directOpenUntil.set(
                                SystemClock.elapsedRealtime() + DIRECT_OPEN_WINDOW_MS,
                            )
                            clearSwipeBouncer(param.thisObject)
                        } else {
                            directOpenUntil.set(0)
                        }
                        InteractionHookLog.info(
                            "NotifyQuickLaunch",
                            "keyguardShowing=${result.showing} canDismiss=${result.canDismiss} " +
                                "deviceLocked=${result.deviceLocked} sourceNotification=${result.notificationSource} " +
                                "-> ${if (result.eligible) "direct-open" else "Samsung behavior"}"
                        )
                    }

                }
            )

            hookSwipeBouncerSetter(starterClass, "activity starter")
            hookSwipeBouncerSetter(keyguardViewManagerClass, "keyguard view manager")

            findAndHookMethod(
                keyguardViewManagerClass,
                "dismissWithAction",
                onDismissActionClass,
                Runnable::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isDirectOpenWindow()) return
                        param.args[3] = true
                        InteractionHookLog.info(
                            "NotifyQuickLaunch",
                            "authenticated notification click: instant dismiss forced",
                        )
                    }
                },
            )
            InteractionHookLog.info("NotifyQuickLaunch", "hooks installed")
        } catch (error: Throwable) {
            directOpenUntil.set(0)
            InteractionHookLog.failure("NotifyQuickLaunch", error)
        }
    }

    private fun hookSwipeBouncerSetter(targetClass: Class<*>, source: String) {
        findAndHookMethod(
            targetClass,
            "setShowSwipeBouncer",
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == true && isDirectOpenWindow()) {
                        param.args[0] = false
                        InteractionHookLog.info(
                            "NotifyQuickLaunch",
                            "authenticated notification click: swipe bouncer suppressed at $source",
                        )
                    }
                }
            },
        )
    }

    private fun clearSwipeBouncer(clicker: Any) {
        try {
            val starter = getObjectField(clicker, "mNotificationActivityStarter")
            val viewManager = getObjectField(starter, "mStatusBarKeyguardViewManager")
            callMethod(viewManager, "setShowSwipeBouncer", false)
            val controller = getObjectField(starter, "mKeyguardStateController")
            runCatching { setBooleanField(controller, "mIsSwipeBouncer", false) }
            InteractionHookLog.info(
                "NotifyQuickLaunch",
                "authenticated notification click: existing swipe bouncer cleared",
            )
        } catch (error: Throwable) {
            InteractionHookLog.failure("NotifyQuickLaunchClearBouncer", error)
        }
    }

    private fun isDirectOpenWindow(): Boolean =
        SystemClock.elapsedRealtime() <= directOpenUntil.get()

    private fun evaluateEligibility(clicker: Any, source: Any?): Eligibility = try {
        val notificationSource = source?.javaClass?.name?.endsWith("ExpandableNotificationRow") == true
        if (!notificationSource) return Eligibility(notificationSource = false)
        val starter = getObjectField(clicker, "mNotificationActivityStarter")
        val controller = getObjectField(starter, "mKeyguardStateController")
        // One UI 8.5's implementation exposes these security states as fields rather than
        // the AOSP interface-style getters. Reading the actual controller state preserves
        // Samsung's Strong Auth and biometric decisions without forcing either value.
        val showing = readBooleanState(controller, "mShowing", "isShowing")
        val canDismiss = readBooleanState(
            controller,
            "mCanDismissLockScreen",
            "canDismissLockScreen"
        )
        val manager = getObjectField(starter, "mKeyguardManager") as? KeyguardManager
        val keyguardLocked = manager?.isKeyguardLocked == true
        val deviceLocked = manager?.isDeviceLocked != false
        Eligibility(
            notificationSource = true,
            showing = showing,
            canDismiss = canDismiss,
            deviceLocked = deviceLocked,
            eligible = showing && canDismiss && keyguardLocked && !deviceLocked,
        )
    } catch (error: Throwable) {
        InteractionHookLog.failure("NotifyQuickLaunch", error)
        Eligibility()
    }

    private fun readBooleanState(target: Any, fieldName: String, methodName: String): Boolean {
        return try {
            getBooleanField(target, fieldName)
        } catch (_: Throwable) {
            (callMethod(target, methodName) as? Boolean) == true
        }
    }

    private fun logMissing(name: String) {
        InteractionHookLog.info("NotifyQuickLaunch", "$name not found; Samsung behavior retained")
    }

    private data class Eligibility(
        val notificationSource: Boolean = false,
        val showing: Boolean = false,
        val canDismiss: Boolean = false,
        val deviceLocked: Boolean = true,
        val eligible: Boolean = false,
    )
}
