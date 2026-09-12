package io.github.soclear.oneuix.hook.systemui

import android.os.Build
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object Other {
    fun disableScreenshotCaptureSound(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            val screenshotCaptureSoundClass = findClass(
                "com.android.systemui.screenshot.${
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "sep."
                    else ""
                }ScreenshotCaptureSound", loadPackageParam.classLoader
            )
            hookAllMethods(screenshotCaptureSoundClass, "play", returnConstant(null))
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
