package io.github.soclear.oneuix.hook.systemui

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import java.util.concurrent.atomic.AtomicBoolean

object LockscreenNavigationBar {
    private const val COMPONENT = "LockscreenNavBar"
    private const val CLASS_NAME =
        "com.android.systemui.statusbar.phone.SecStatusBarKeyguardViewManager"
    private val stateReadFailureLogged = AtomicBoolean(false)

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
                        if (param.result == true) return
                        try {
                            val keyguardStateController = getObjectField(
                                param.thisObject,
                                "mKeyguardStateController",
                            )
                            val showing = getBooleanField(keyguardStateController, "mShowing")
                            val occluded = getBooleanField(keyguardStateController, "mOccluded")
                            val dozing = getBooleanField(param.thisObject, "mDozing")
                            val screenOffAnimationPlaying = getBooleanField(
                                param.thisObject,
                                "mScreenOffAnimationPlaying",
                            )
                            if (showing && !occluded && !dozing && !screenOffAnimationPlaying) {
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
            InteractionHookLog.info(
                COMPONENT,
                "lockscreen navigation bar visibility override installed",
            )
        } catch (error: Throwable) {
            InteractionHookLog.failure(COMPONENT, error)
        }
    }
}
