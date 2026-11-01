package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
}
