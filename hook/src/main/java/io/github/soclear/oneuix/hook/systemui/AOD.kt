package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

object AOD {
    @SuppressLint("PrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideAODStatusBar() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }
        try {
            param.classLoader
                .loadClass("com.android.systemui.battery.BatteryMeterViewController")
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
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun aodLockSupportLunar() {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            val semCscFeatureClass = param.classLoader.loadClass("com.samsung.android.feature.SemCscFeature")

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
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
