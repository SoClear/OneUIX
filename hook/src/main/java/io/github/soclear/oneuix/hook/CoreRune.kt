package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object CoreRune {
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun supportAppJumpBlockAndroid() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        try {
            val serviceClass = param.classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService")
            if (AppJumpBlockFlag.isFinal(param.classLoader)) {
                // EZIH retains these three field read sites. Discard compiled constant values
                // before the service constructor enables the runtime-initialized CSC flag.
                val readers = setOf("onSystemReady", "startActivities", "startActivityAsUser")
                val methods = serviceClass.declaredMethods.filter { it.name in readers }
                check(methods.map { it.name }.toSet() == readers) { "App-redirect read sites differ" }
                methods.forEach { check(xposedModule.deoptimize(it)) { "Cannot deoptimize ${it.name}" } }
            }
            xposedModule.hook(
                serviceClass.getDeclaredConstructor(Context::class.java)
            ).intercept { chain ->
                try {
                    AppJumpBlockFlag.enable(param.classLoader)
                    xlog("App redirect compatibility: system flag enabled and verified", priority = Log.INFO)
                } catch (t: Throwable) {
                    xlog(t)
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportAppJumpBlockSettings() {
        if (param.packageName != Package.SETTINGS ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }
        val infix =
            if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                "security"
            } else {
                "privacy"
            }
        afterAttach {
            try {
                xposedModule.hook(
                    param.classLoader
                        .loadClass("com.samsung.android.settings.$infix.AppRedirectInterceptionPreferenceController")
                        .getDeclaredMethod("getAvailabilityStatus")
                // Expose the preference directly; its checked state and writes still use
                // Samsung's original appJumpBlock setting. No final-field write is needed here.
                ).intercept { 0 /* BasePreferenceController.AVAILABLE */ }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    @SuppressLint("BlockedPrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun allowAllRotation() {
        if (ONE_UI_VERSION >= 90000) {
            try {
                val displayRotationClass =
                    param.classLoader.loadClass("com.android.server.wm.DisplayRotation")
                val allowAllRotations = displayRotationClass.reflect.findField("mAllowAllRotations")
                // CoreRune flags are static final on newer ROMs. Set the mutable cache at
                // its read sites instead; this also survives the cache being reset to -1.
                // Match by name because the method signature differs between Android versions.
                val methods =
                    displayRotationClass.declaredMethods.filter { it.name == "rotationForOrientation" }
                if (methods.isEmpty()) throw NoSuchMethodException("DisplayRotation.rotationForOrientation")
                methods.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        allowAllRotations.setInt(chain.thisObject, 1)
                        chain.proceed()
                    }
                }
            } catch (t: Throwable) {
                xlog("allowAllRotation: automatic rotation hook failed", t)
            }

            try {
                val displayRotationClass =
                    param.classLoader.loadClass("com.android.server.wm.DisplayRotation")
                val allowAllRotations = displayRotationClass.reflect.findField("mAllowAllRotations")
                val listenerClass =
                    param.classLoader.loadClass($$"com.android.server.wm.DisplayRotation$OrientationListener")
                val displayRotation = listenerClass.reflect.findField($$"this$0")
                // Rotation suggestions can read the cache before rotationForOrientation runs.
                xposedModule.hook(
                    listenerClass.getDeclaredMethod(
                        "onProposedRotationChanged",
                        Int::class.javaPrimitiveType
                    )
                ).intercept { chain ->
                    allowAllRotations.setInt(displayRotation.get(chain.thisObject), 1)
                    chain.proceed()
                }
            } catch (t: Throwable) {
                xlog("allowAllRotation: rotation suggestion hook failed", t)
            }
        } else {
            try {
                val coreRuneClass = param.classLoader.loadClass("com.samsung.android.rune.CoreRune")
                coreRuneClass.reflect["FW_ALLOW_ALL_ROTATION"] = true
                coreRuneClass.reflect["FW_ORIENTATION_CONTROL"] = true
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        try {
            xposedModule.hook(
                param.classLoader.loadClass("com.android.internal.view.RotationPolicy")
                    .getDeclaredMethod("areAllRotationsAllowed", Context::class.java)
            ).intercept { true }
        } catch (t: Throwable) {
            xlog("allowAllRotation: RotationPolicy hook failed", t)
        }
    }
}
