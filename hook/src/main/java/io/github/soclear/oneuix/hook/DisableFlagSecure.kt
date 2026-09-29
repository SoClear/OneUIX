package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.SurfaceControl
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.function.BiConsumer
import java.util.function.BiPredicate

@SuppressLint("PrivateApi", "BlockedPrivateApi")
object DisableFlagSecure {

    context(xposedModule: XposedModule)
    fun hookSystemServer(classLoader: ClassLoader) {
        deoptimizeSystemServer(classLoader)
        hookWindowState(classLoader)
        hookScreenCapture(classLoader)
        hookScreenshotHardwareBuffer(classLoader)
        hookOneUI(classLoader)
        hookVirtualDisplayAdapter(classLoader)
        hookDisplayControl(classLoader)
        hookDetection(classLoader)
        hookBlackoutPermission(classLoader)
    }

    context(xposedModule: XposedModule)
    fun hookSystemUI(classLoader: ClassLoader) {
        hookScreenshotHardwareBuffer(classLoader)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hookScreenCapture(classLoader)
        }
    }

    context(xposedModule: XposedModule)
    private fun deoptimizeSystemServer(classLoader: ClassLoader) {
        fun deopt(clazz: Class<*>, vararg names: String) =
            clazz.declaredMethods.filter { it.name in names }.forEach { xposedModule.deoptimize(it) }

        runCatching {
            deopt(classLoader.loadClass("com.android.server.wm.WindowStateAnimator"), "createSurfaceLocked")
            deopt(classLoader.loadClass("com.android.server.wm.WindowManagerService"), "relayoutWindow")
            for (i in 0..19) {
                runCatching {
                    val clz =
                        classLoader.loadClass($$$"com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda$$$i")
                    if (BiConsumer::class.java.isAssignableFrom(clz)) deopt(clz, "accept")
                }
                runCatching {
                    val clz = classLoader.loadClass($$"com.android.server.wm.DisplayContent$$$i")
                    if (BiPredicate::class.java.isAssignableFrom(clz)) deopt(clz, "test")
                }
            }
        }
    }

    context(xposedModule: XposedModule)
    private fun hookWindowState(classLoader: ClassLoader) = runCatching {
        val windowStateClass = classLoader.loadClass("com.android.server.wm.WindowState")
        val systemServerCl = windowStateClass.classLoader
        xposedModule.hook(windowStateClass.getDeclaredMethod("isSecureLocked")).intercept { chain ->
            val allow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { frames ->
                    frames.anyMatch {
                        it.declaringClass?.classLoader == systemServerCl &&
                                (it.methodName == "setInitialSurfaceControlProperties" || it.methodName == "createSurfaceLocked")
                    }
                }
            } else {
                Throwable().stackTrace.any { frame ->
                    (frame.methodName == "setInitialSurfaceControlProperties" || frame.methodName == "createSurfaceLocked") &&
                            runCatching { classLoader.loadClass(frame.className).classLoader == systemServerCl }.getOrDefault(
                                false
                            )
                }
            }
            if (allow) chain.proceed() else false
        }
    }

    context(xposedModule: XposedModule)
    private fun hookScreenCapture(classLoader: ClassLoader) = runCatching {
        val (screenCaptureClass, captureArgsClass) = runCatching {
            classLoader.loadClass("android.window.ScreenCaptureInternal") to
                    classLoader.loadClass($$"android.window.ScreenCaptureInternal$CaptureArgs")
        }.recoverCatching {
            classLoader.loadClass("android.window.ScreenCapture") to
                    classLoader.loadClass($$"android.window.ScreenCapture$CaptureArgs")
        }.getOrElse {
            SurfaceControl::class.java to classLoader.loadClass($$"android.view.SurfaceControl$CaptureArgs")
        }
        val field = runCatching { captureArgsClass.getDeclaredField("mSecureContentPolicy") }
            .getOrElse { captureArgsClass.getDeclaredField("mCaptureSecureLayers") }
            .apply { isAccessible = true }
        val isPolicy = field.name == "mSecureContentPolicy"

        val hooker: (XposedInterface.Chain) -> Any? = { chain ->
            runCatching { field.set(chain.args[0], if (isPolicy) 1 else true) }
            chain.proceed()
        }
        screenCaptureClass.declaredMethods
            .filter { it.name == "nativeCaptureDisplay" || it.name == "nativeCaptureLayers" }
            .forEach { xposedModule.hook(it).intercept(hooker) }
    }

    context(xposedModule: XposedModule)
    private fun hookScreenshotHardwareBuffer(classLoader: ClassLoader) = runCatching {
        val bufferClass = runCatching {
            classLoader.loadClass($$"android.window.ScreenCapture$ScreenshotHardwareBuffer")
        }.getOrElse {
            classLoader.loadClass($$"android.view.SurfaceControl$ScreenshotHardwareBuffer")
        }
        bufferClass.declaredMethods.filter { it.name == "containsSecureLayers" }.forEach {
            xposedModule.hook(it).intercept { false }
        }
    }

    context(xposedModule: XposedModule)
    private fun hookOneUI(classLoader: ClassLoader) = runCatching {
        classLoader.loadClass("com.android.server.wm.WmScreenshotController").declaredMethods
            .filter { it.name == "canBeScreenshotTarget" }.forEach {
                xposedModule.hook(it).intercept { true }
            }
    }

    context(xposedModule: XposedModule)
    private fun hookVirtualDisplayAdapter(classLoader: ClassLoader) = runCatching {
        classLoader.loadClass("com.android.server.display.VirtualDisplayAdapter").declaredMethods
            .filter { it.name == "createVirtualDisplayLocked" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val caller = chain.args.getOrNull(2) as? Int ?: 0
                    if (caller < 10000 || chain.args.getOrNull(1) != null) {
                        for (i in 3 until chain.args.size) {
                            val flags = chain.args[i] as? Int ?: continue
                            val newArgs = chain.args.toTypedArray()
                            newArgs[i] = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
                            return@intercept chain.proceed(newArgs)
                        }
                    }
                    chain.proceed()
                }
            }
    }

    context(xposedModule: XposedModule)
    private fun hookDisplayControl(classLoader: ClassLoader) = runCatching {
        val displayClass = runCatching {
            classLoader.loadClass("com.android.server.display.DisplayControl")
        }.getOrElse { SurfaceControl::class.java }
        val systemServerCl = displayClass.classLoader
        displayClass.declaredMethods
            .filter {
                (it.name == "createVirtualDisplay" || it.name == "createDisplay") &&
                        it.parameterTypes.size == 2 && it.parameterTypes[1] == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val fromVDA = Throwable().stackTrace.any { frame ->
                            frame.methodName == "createVirtualDisplayLocked" &&
                                    runCatching {
                                        classLoader.loadClass(frame.className).classLoader == systemServerCl
                                    }.getOrDefault(
                                        false
                                    )
                        }
                        if (fromVDA) return@intercept chain.proceed()
                    }
                    val newArgs = chain.args.toTypedArray()
                    newArgs[1] = true
                    chain.proceed(newArgs)
                }
            }
    }

    context(xposedModule: XposedModule)
    private fun hookDetection(classLoader: ClassLoader) {
        runCatching {
            classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService").declaredMethods
                .filter { it.name == "registerScreenCaptureObserver" }.forEach {
                    xposedModule.hook(it).intercept { null }
                }
        }
        runCatching {
            classLoader.loadClass("com.android.server.wm.WindowManagerService").declaredMethods
                .filter { it.name == "registerScreenRecordingCallback" }.forEach {
                    xposedModule.hook(it).intercept { false }
                }
        }
    }

    context(xposedModule: XposedModule)
    private fun hookBlackoutPermission(classLoader: ClassLoader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            classLoader.loadClass("com.android.server.am.ActivityManagerService").declaredMethods
                .filter { it.name == "checkPermission" && it.parameterTypes.firstOrNull() == String::class.java }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        if (chain.args.firstOrNull() == "android.permission.CAPTURE_BLACKOUT_CONTENT") {
                            val newArgs = chain.args.toTypedArray()
                            newArgs[0] = "android.permission.READ_FRAME_BUFFER"
                            chain.proceed(newArgs)
                        } else chain.proceed()
                    }
                }
        }
    }
}
