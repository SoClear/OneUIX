package io.github.soclear.oneuix.hook.systemui

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

/**
 * The lock screen notification icon row is drawn by the Samsung FaceWidget plugin
 * (classes from com.samsung.android.app.aodservice, loaded into SystemUI through a
 * separate PathClassLoader). Its controller truncates the icons to a hard-coded cap
 * (keyguard status bar = 2, lock screen = 4, ...), so the "max notification icons"
 * setting never reaches it.
 *
 * The plugin classes are obfuscated and only appear at runtime, so we hook the stable
 * SystemUI entry point FaceWidgetNotificationControllerWrapper.initPlugin to obtain the
 * plugin's class loader, then use DexKit to locate the truncation method
 * `static void f(controller, ArrayList)` and lift its cap to the user's value.
 */
object LockscreenNotificationIcons {
    // NIOType returned by the container: keyguard_lockstar, whose cap is the writable
    // field mLockStarThreshold (all other types use hard-coded literals).
    private const val TYPE_KEYGUARD_LOCKSTAR = 5

    fun setMaxLockscreenNotificationIcons(loadPackageParam: LoadPackageParam, max: Int) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || max < 0) return

        try {
            findAndHookMethod(
                "com.android.systemui.facewidget.plugin.FaceWidgetNotificationControllerWrapper",
                loadPackageParam.classLoader,
                "initPlugin",
                "com.android.systemui.plugins.keyguardstatusview.PluginNotificationController",
                android.content.Context::class.java,
                object : XC_MethodHook() {
                    @Volatile
                    private var hooked = false

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val plugin = param.args[0] ?: return
                        if (hooked) return
                        val pluginClassLoader = plugin.javaClass.classLoader ?: return
                        synchronized(this) {
                            if (hooked) return
                            if (hookTruncation(pluginClassLoader, max)) hooked = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    /** Locate the controller's truncation method in the plugin dex and hook it. */
    private fun hookTruncation(classLoader: ClassLoader, max: Int): Boolean {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            // static void f(<controller>, ArrayList) that reads the container's NIOType.
            val fMethod = bridge.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = "void"
                    paramCount = 2
                    paramTypes(null, "java.util.ArrayList")
                    invokeMethods { add { name = "getNIOType" } }
                }
            }.singleOrNull()?.getMethodInstance(classLoader) ?: return false

            // Field that holds the lockstar cap (protected int mLockStarThreshold).
            val thresholdField = "mLockStarThreshold"

            hookMethod(fMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val controller = param.args[0] ?: return
                    // Drive the cap to the user's value and make the container report the
                    // lockstar type so f() picks the cap up from mLockStarThreshold.
                    setIntField(controller, thresholdField, max)
                    forcedType.set(getContainer(controller) ?: return)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    forcedType.remove()
                }
            })

            // Make the affected container report lockstar type for the duration of f().
            findAndHookMethod(
                "com.samsung.android.uniform.widget.notification.NotificationIconsOnlyContainer",
                classLoader,
                "getNIOType",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (forcedType.get() === param.thisObject) {
                            param.result = TYPE_KEYGUARD_LOCKSTAR
                        }
                    }
                }
            )
        }
        return true
    }

    private fun getContainer(controller: Any): Any? = try {
        getObjectField(controller, "mNotificationContainer")
    } catch (_: Throwable) {
        null
    }

    // The container currently inside f(); its getNIOType is remapped to lockstar.
    private val forcedType = ThreadLocal<Any?>()
}
