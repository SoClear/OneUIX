package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object AOD {
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
}
