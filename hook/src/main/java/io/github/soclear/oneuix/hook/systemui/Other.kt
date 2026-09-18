package io.github.soclear.oneuix.hook.systemui

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.xlog

object Other {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun disableScreenshotCaptureSound() = afterAttach {
        if (param.packageName != Package.SYSTEMUI) return@afterAttach
        try {
            param.classLoader
                .loadClass(
                    "com.android.systemui.screenshot.${
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "sep."
                        else ""
                    }ScreenshotCaptureSound"
                )
                .declaredMethods
                .filter { it.name == "play" }
                .forEach {
                    xposedModule.hook(it).intercept { null }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
