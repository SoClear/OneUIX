package io.github.soclear.oneuix.hook.systemui

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object LockscreenNavigationBar {
    private const val COMPONENT = "LockscreenNavBar"
    private const val CLASS_NAME =
        "com.android.systemui.statusbar.phone.SecStatusBarKeyguardViewManager"
    private val stateReadFailureLogged = AtomicBoolean(false)
    private var managerRef: WeakReference<Any>? = null

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
            InteractionHookLog.info(
                COMPONENT,
                "lockscreen navigation bar window and gesture handle overrides installed",
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
}
