package io.github.soclear.oneuix.hook.systemui

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object LockscreenNavigationBar {
    private const val COMPONENT = "LockscreenNavBar"
    private const val CLASS_NAME =
        "com.android.systemui.statusbar.phone.SecStatusBarKeyguardViewManager"
    private val stateReadFailureLogged = AtomicBoolean(false)
    private var managerRef: WeakReference<Any>? = null
    private val navigationHandles = CopyOnWriteArrayList<WeakReference<View>>()

    fun showOnLockscreen(lpparam: LoadPackageParam) {
        try {
            val managerClass = findClassIfExists(CLASS_NAME, lpparam.classLoader) ?: run {
                InteractionHookLog.info(COMPONENT, "Samsung keyguard view manager unavailable")
                return
            }
            findAndHookMethod(
                managerClass,
                "isNavBarVisible",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        managerRef = WeakReference(param.thisObject)
                        if (param.result == true) return
                        try {
                            if (shouldShowOnLockscreen(param.thisObject)) {
                                param.result = true
                            }
                        } catch (error: Throwable) {
                            if (stateReadFailureLogged.compareAndSet(false, true)) {
                                InteractionHookLog.failure(COMPONENT, error)
                            }
                        }
                    }
                },
            )
            findAndHookMethod(
                "com.android.systemui.navigationbar.views.SamsungNavigationBarView",
                lpparam.classLoader,
                "updateHintVisibility",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val manager = managerRef?.get() ?: return
                        try {
                            if (shouldShowOnLockscreen(manager)) {
                                param.args[1] = true
                            }
                        } catch (error: Throwable) {
                            if (stateReadFailureLogged.compareAndSet(false, true)) {
                                InteractionHookLog.failure(COMPONENT, error)
                            }
                        }
                    }
                },
            )
            installGestureHandleMotion(lpparam)
            InteractionHookLog.info(
                COMPONENT,
                "lockscreen navigation bar window, gesture handle, and swipe motion overrides installed",
            )
        } catch (error: Throwable) {
            InteractionHookLog.failure(COMPONENT, error)
        }
    }

    private fun shouldShowOnLockscreen(manager: Any): Boolean {
        val keyguardStateController = getObjectField(manager, "mKeyguardStateController")
        return getBooleanField(keyguardStateController, "mShowing") &&
            !getBooleanField(keyguardStateController, "mOccluded") &&
            !getBooleanField(manager, "mDozing") &&
            !getBooleanField(manager, "mScreenOffAnimationPlaying")
    }

    private fun installGestureHandleMotion(lpparam: LoadPackageParam) {
        findAndHookConstructor(
            "com.android.systemui.navigationbar.gestural.NavigationHandle",
            lpparam.classLoader,
            Context::class.java,
            AttributeSet::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    navigationHandles.removeAll { it.get() == null }
                    navigationHandles += WeakReference(param.thisObject as View)
                }
            },
        )

        val touchBaseClass = findClassIfExists(
            "com.android.systemui.keyguard.animator.KeyguardTouchBase",
            lpparam.classLoader,
        ) ?: return
        findAndHookMethod(
            touchBaseClass,
            "updateDistance",
            MotionEvent::class.java,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val manager = managerRef?.get() ?: return
                    if (!shouldShowOnLockscreen(manager)) return
                    val event = param.args[0] as MotionEvent
                    val down = getObjectField(param.thisObject, "touchDownPos") as PointF
                    val upwardDistance = (down.y - event.rawY).coerceAtLeast(0f)
                    val unlockRadius = max(getIntField(param.thisObject, "swipeUnlockRadius"), 1)
                    val progress = (upwardDistance / unlockRadius).coerceIn(0f, 1f)
                    updateHandles(progress)
                }
            },
        )
        findAndHookMethod(
            touchBaseClass,
            "setTouch",
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == false) resetHandles(animated = true)
                }
            },
        )
    }

    private fun updateHandles(progress: Float) {
        navigationHandles.removeAll { it.get() == null }
        navigationHandles.forEach { reference ->
            reference.get()?.let { handle ->
                if (!handle.isAttachedToWindow || handle.visibility != View.VISIBLE) return@let
                handle.animate().cancel()
                val maxTravel = 28f * handle.resources.displayMetrics.density
                handle.translationY = -maxTravel * progress
                handle.scaleX = 1f + (0.35f * progress)
                handle.alpha = 0.82f + (0.18f * progress)
            }
        }
    }

    private fun resetHandles(animated: Boolean) {
        navigationHandles.removeAll { it.get() == null }
        navigationHandles.forEach { reference ->
            reference.get()?.let { handle ->
                handle.animate().cancel()
                if (animated && handle.isAttachedToWindow) {
                    handle.animate()
                        .translationY(0f)
                        .scaleX(1f)
                        .alpha(1f)
                        .setDuration(220L)
                        .start()
                } else {
                    handle.translationY = 0f
                    handle.scaleX = 1f
                    handle.alpha = 1f
                }
            }
        }
    }
}
