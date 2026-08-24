package io.github.soclear.oneuix.hook.systemui

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.findFieldIfExists
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.hook.util.InteractionHookLog
import kotlin.math.roundToInt

object KeyguardGesture {
    fun customizeSwipeDistance(lpparam: LoadPackageParam, requestedScale: Float) {
        val scale = requestedScale.coerceIn(0.1f, 1.0f)
        try {
            InteractionHookLog.environment("KeyguardGesture", lpparam)
            val keyguardTouchBase = findClassIfExists(
                "com.android.systemui.keyguard.animator.KeyguardTouchBase",
                lpparam.classLoader
            ) ?: run {
                InteractionHookLog.info("KeyguardGesture", "KeyguardTouchBase not found; Samsung behavior retained")
                return
            }
            val radiusField = findFieldIfExists(keyguardTouchBase, "swipeUnlockRadius") ?: run {
                InteractionHookLog.info("KeyguardGesture", "swipeUnlockRadius not found; Samsung behavior retained")
                return
            }
            val candidates = keyguardTouchBase.declaredMethods.filter {
                it.parameterCount == 0 && it.name.startsWith("initDimens")
            }
            val initMethod = candidates.firstOrNull { it.name == "initDimens\$5" }
                ?: candidates.singleOrNull()
                ?: run {
                    InteractionHookLog.info(
                        "KeyguardGesture",
                        "safe initDimens match unavailable (${candidates.size} candidates); Samsung behavior retained"
                    )
                    return
                }

            XposedBridge.hookMethod(initMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val originalRadius = radiusField.getInt(param.thisObject)
                        if (originalRadius <= 0) return
                        val result = (originalRadius * scale).roundToInt().coerceAtLeast(1)
                        radiusField.setInt(param.thisObject, result)
                        InteractionHookLog.info(
                            "KeyguardGesture",
                            "swipeUnlockRadius original=$originalRadius scale=$scale result=$result"
                        )
                    } catch (error: Throwable) {
                        InteractionHookLog.failure("KeyguardGesture", error)
                    }
                }
            })
            InteractionHookLog.info("KeyguardGesture", "hooked=${initMethod.name}")
        } catch (error: Throwable) {
            InteractionHookLog.failure("KeyguardGesture", error)
        }
    }
}
