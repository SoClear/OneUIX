package io.github.soclear.oneuix.hook.util

import android.os.Build
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.ONE_UI_VERSION

object InteractionHookLog {
    fun environment(component: String, lpparam: LoadPackageParam) {
        info(
            component,
            "package=${lpparam.packageName} Android=${Build.VERSION.RELEASE} " +
                "API=${Build.VERSION.SDK_INT} OneUI=$ONE_UI_VERSION"
        )
    }

    fun info(component: String, message: String) {
        XposedBridge.log("OneUIX [$component] $message")
    }

    fun failure(component: String, error: Throwable) {
        info(component, "failed=${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        XposedBridge.log(error)
    }
}
