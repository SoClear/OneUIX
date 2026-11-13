package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object AOD {
    context(xposedModule: XposedModule)
    fun wallpaperOnly() = afterAttachTry {
        val hiddenViews = mutableMapOf<View, Float>()
        val scrimViewClass = classLoader.loadClass("com.android.systemui.scrim.ScrimView")
        val lightRevealScrimClass = classLoader.loadClass("com.android.systemui.statusbar.LightRevealScrim")
        classLoader.loadClass("com.android.systemui.doze.PluginAODManager")
            .getDeclaredMethod("setIsDozing", Boolean::class.java, Boolean::class.java)
            .let { xposedModule.hook(it) }
            .intercept { chain ->
                hiddenViews.forEach { (view, alpha) -> view.transitionAlpha = alpha }
                hiddenViews.clear()
                val result = chain.proceed()
                if (chain.args[0] == true) {
                    val host = chain.thisObject.reflect["mDozeServiceHostLazy"]?.reflect?.call("get")
                    val view = host?.reflect?.call("getNotificationPanelView") as? ViewGroup
                    if (view != null) {
                        for (index in 0 until view.childCount) {
                            val child = view.getChildAt(index)
                            if (!scrimViewClass.isInstance(child) && !lightRevealScrimClass.isInstance(child)) {
                                hiddenViews[child] = child.transitionAlpha
                                child.transitionAlpha = 0f
                            }
                        }
                    }
                }
                result
            }
    }

    context(xposedModule: XposedModule)
    fun hideAODChargingInfo() = afterAttachTry {
        val hookedClasses = mutableSetOf<Class<*>>()
        classLoader.loadClass("com.android.systemui.doze.PluginAODManager")
            .getDeclaredMethod("setAODPlugin", classLoader.loadClass("com.android.systemui.plugins.aod.PluginAOD"))
            .let { xposedModule.hook(it) }
            .intercept { chain ->
                try {
                    // AOD views are loaded by the plugin's class loader.
                    val viewClass = chain.args[0]?.javaClass?.classLoader
                        ?.loadClass("com.samsung.android.uniform.widget.charginginfo.ChargingInfoView")
                    if (viewClass != null && hookedClasses.add(viewClass)) {
                        xposedModule.hook(viewClass.getDeclaredMethod("onFinishInflate"))
                            .intercept { viewChain ->
                                val result = viewChain.proceed()
                                (viewChain.thisObject as View).visibility = View.GONE
                                result
                            }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                chain.proceed()
            }
    }

    @SuppressLint("PrivateApi")
    context(xposedModule: XposedModule)
    fun hideAODStatusBar() = afterAttachTry(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        classLoader.loadClass("com.android.systemui.battery.BatteryMeterViewController")
            .getDeclaredMethod("onViewAttached")
            .let { xposedModule.hook(it) }
            .intercept { chain ->
                val result = chain.proceed()
                try {
                    val batteryView = chain.thisObject.reflect["mView"] as View
                    if (batteryView.tag == "PluginFaceWidgetManager") {
                        val parentView = batteryView.parent.parent as View
                        parentView.visibility = View.GONE
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
    }

    context(xposedModule: XposedModule)
    fun aodLockSupportLunar() = afterAttachTry {
        val semCscFeatureClass = classLoader.loadClass("com.samsung.android.feature.SemCscFeature")

        val hooker = XposedInterface.Hooker { chain ->
            if (chain.args[0] == "CscFeature_Calendar_EnableLocalHolidayDisplay") {
                "CHINA"
            } else {
                chain.proceed()
            }
        }

        listOf(
            semCscFeatureClass.getDeclaredMethod("getString", String::class.java),
            semCscFeatureClass.getDeclaredMethod("getString", String::class.java, String::class.java)
        ).forEach { method ->
            xposedModule.hook(method).intercept(hooker)
        }
    }

    context(xposedModule: XposedModule)
    fun removeAODBrightnessLimit() = afterAttachTry {
        val resolver = contentResolver
        val brightnessClass = classLoader.loadClass("com.android.systemui.doze.AODScreenBrightness")
        val minField = runCatching { brightnessClass.reflect.findField("brightnessMin") }.getOrNull()
        val maxField = runCatching { brightnessClass.reflect.findField("brightnessMax") }.getOrNull()
        brightnessClass.getDeclaredMethod("updateDozeBrightness", Int::class.java, Int::class.java, Int::class.java)
            .let { xposedModule.hook(it) }
            .intercept { chain ->
                val args = chain.args.toTypedArray()
                try {
                    val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
                    if (current >= 0) {
                        val min = minField?.getInt(chain.thisObject) ?: 1
                        val max = maxField?.getInt(chain.thisObject) ?: 255
                        args[2] = current.coerceIn(min, max)
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                chain.proceed(args)
            }
    }
}
