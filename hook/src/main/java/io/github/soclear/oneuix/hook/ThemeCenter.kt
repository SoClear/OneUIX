package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object ThemeCenter {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setTrialNeverExpired() {
        if (param.packageName != Package.THEME_CENTER) return
        try {
            val periodManagerClass =
                param.classLoader.loadClass("com.samsung.android.thememanager.period.PeriodManager")
            periodManagerClass.declaredMethods.filter {
                it.name == "setAlarm"
            }.forEach {
                xposedModule.hook(it).intercept { null }
            }

            xposedModule.hook(
                param.classLoader.loadClass("com.samsung.android.thememanager.period.ThemeNotiUtils")
                    .getDeclaredMethod("setTrialExpiredPackage", String::class.java)
            ).intercept { null }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
