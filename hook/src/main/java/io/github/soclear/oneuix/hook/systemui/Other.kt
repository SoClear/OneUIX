package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("PrivateApi")
object Other {
    context(xposedModule: XposedModule)
    fun hideLockscreenEmergencyButton() = afterAttachTry {
        val controller = classLoader.loadClass("com.android.keyguard.EmergencyButtonController")
        val viewField = controller.reflect.findField("mView")
        xposedModule.hook(controller.getDeclaredMethod("updateEmergencyCallButton")).intercept { chain ->
            (viewField.get(chain.thisObject) as? View)?.visibility = View.GONE
            null
        }
    }

    context(xposedModule: XposedModule)
    fun allowLowBatteryFlashlight() = afterAttachTry {
        val tile = classLoader.loadClass("com.android.systemui.qs.tiles.FlashlightTile")
        val composeReceiver = runCatching {
            classLoader.loadClass(
                $$"com.android.systemui.samsung.quicksetting.data.repository.SecFlashlightRepositoryImpl$lowBattery$1$broadcastReceiver$1"
            )
        }.getOrNull()
        listOfNotNull(tile.getDeclaredField("mReceiver").type, composeReceiver).forEach { receiver ->
            val method = receiver.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
            xposedModule.hook(method).intercept { chain ->
                val intent = chain.args[1] as Intent
                if (intent.action == Intent.ACTION_BATTERY_CHANGED &&
                    intent.getIntExtra("level", 0) in 1..5
                ) {
                    // Only this flashlight receiver sees 6%; other battery consumers stay untouched.
                    val args = chain.args.toTypedArray()
                    args[1] = Intent(intent).putExtra("level", 6)
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }
        }
    }

    context(xposedModule: XposedModule)
    fun disableScreenshotCaptureSound() = afterAttachTry {
        classLoader
            .loadClass(
                "com.android.systemui.screenshot.${
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "sep."
                    else ""
                }ScreenshotCaptureSound"
            )
            .declaredMethods
            .filter { it.name == "play" }
            .forEach {
                xposedModule.hook(it).intercept { }
            }
    }

    context(xposedModule: XposedModule)
    fun deferFaceUnlockUntilSwipe() = afterAttachTry(ONE_UI_VERSION >= 80500) {
        val monitorClass = classLoader.loadClass("com.android.keyguard.KeyguardSecUpdateMonitorImpl")

        // Members may be inherited from KeyguardUpdateMonitor.
        fun fieldOrNull(type: Class<*>, name: String): Field? = runCatching {
            type.getDeclaredField(name).apply { isAccessible = true }
        }.getOrNull()

        fun methodOrNull(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
            runCatching { type.getDeclaredMethod(name, *parameterTypes) }.getOrNull()

        fun findField(name: String): Field? {
            var type: Class<*>? = monitorClass
            while (type != null) {
                fieldOrNull(type, name)?.let { return it }
                type = type.superclass
            }
            return null
        }

        fun findMethod(name: String, vararg parameterTypes: Class<*>): Method? {
            var type: Class<*>? = monitorClass
            while (type != null) {
                methodOrNull(type, name, *parameterTypes)?.let { return it }
                type = type.superclass
            }
            return null
        }

        val willShowField = findField("mPrimaryBouncerIsOrWillBeShowing")
        val fullyShownField = findField("mPrimaryBouncerFullyShown")
        val shouldListenForFaceMethod = findMethod("shouldListenForFace")
        val bouncerChangedMethod = findMethod(
            "handlePrimaryBouncerChanged",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )

        // Don't suppress face recognition permanently if the state is unavailable.
        if ((willShowField == null && fullyShownField == null) ||
            shouldListenForFaceMethod == null || bouncerChangedMethod == null
        ) {
            xlog(
                "deferFaceUnlockUntilSwipe: required members not found " +
                    "(fields=${willShowField != null}/${fullyShownField != null}, " +
                    "methods=${shouldListenForFaceMethod != null}/${bouncerChangedMethod != null})",
                priority = Log.WARN
            )
            return@afterAttachTry
        }

        val bouncerVisible = AtomicBoolean(false)

        fun readBouncerVisible(monitor: Any): Boolean {
            fun read(field: Field?): Boolean =
                field != null && runCatching { field.getBoolean(monitor) }.getOrDefault(false)
            return read(willShowField) || read(fullyShownField)
        }

        xposedModule.hook(shouldListenForFaceMethod).intercept { chain ->
            if (!bouncerVisible.get()) {
                bouncerVisible.set(readBouncerVisible(chain.thisObject))
            }
            if (bouncerVisible.get()) {
                chain.proceed()
            } else {
                false
            }
        }

        xposedModule.hook(bouncerChangedMethod).intercept { chain ->
            val isOrWillBeShowing = (chain.args.getOrNull(0) as? Int) == 1
            val fullyShown = (chain.args.getOrNull(1) as? Int) == 1
            val visible = isOrWillBeShowing || fullyShown
            bouncerVisible.set(visible)
            chain.proceed()
        }
    }
}
