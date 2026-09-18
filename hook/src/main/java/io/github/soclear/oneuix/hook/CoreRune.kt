package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.set
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object CoreRune {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportAppJumpBlock() {
        if (param.packageName != Package.ANDROID &&
            param.packageName != Package.SETTINGS ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }

        try {
            if (param.packageName == Package.ANDROID) {
                xposedModule.hook(
                    param.classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService")
                        .getDeclaredConstructor(Context::class.java)
                ).intercept { chain ->
                    try {
                        param.classLoader.loadClass("com.samsung.android.rune.CoreRune")["SUPPORT_APP_JUMP_BLOCK"] = true
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                    chain.proceed()
                }
            }
            if (param.packageName == Package.SETTINGS) {
                val infix =
                    if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                        "security"
                    } else {
                        "privacy"
                    }
                xposedModule.hook(
                    param.classLoader.loadClass("com.samsung.android.settings.$infix.AppRedirectInterceptionPreferenceController")
                        .getDeclaredMethod("getAvailabilityStatus")
                ).intercept { chain ->
                    try {
                        param.classLoader.loadClass("com.samsung.android.rune.CoreRune")["SUPPORT_APP_JUMP_BLOCK"] = true
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    @SuppressLint("BlockedPrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun allowAllRotation() {
        if (param.packageName != Package.ANDROID) {
            return
        }

        try {
            val coreRuneClass = param.classLoader.loadClass("com.samsung.android.rune.CoreRune")
            coreRuneClass["FW_ALLOW_ALL_ROTATION"] = true
            coreRuneClass["FW_ORIENTATION_CONTROL"] = true
            xposedModule.hook(
                param.classLoader.loadClass("com.android.internal.view.RotationPolicy")
                    .getDeclaredMethod("areAllRotationsAllowed", Context::class.java)
            ).intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
