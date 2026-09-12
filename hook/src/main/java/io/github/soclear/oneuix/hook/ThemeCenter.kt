package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.soclear.oneuix.common.Package

object ThemeCenter {
    fun setTrialNeverExpired(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (loadPackageParam.packageName != Package.THEME_CENTER) return
        try {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass(
                    "com.samsung.android.thememanager.period.PeriodManager",
                    loadPackageParam.classLoader
                ),
                "setAlarm",
                XC_MethodReplacement.DO_NOTHING
            )

            XposedHelpers.findAndHookMethod(
                "com.samsung.android.thememanager.period.ThemeNotiUtils",
                loadPackageParam.classLoader,
                "setTrialExpiredPackage",
                String::class.java,
                XC_MethodReplacement.DO_NOTHING
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
