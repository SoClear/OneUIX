package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect

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
}
