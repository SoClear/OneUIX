package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier


object HealthMonitor {
    private const val TAG = "OneUIX HealthMonitor"

    fun bypassCountryCheck(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.HEALTH_MONITOR) {
            return
        }
        afterAttach {
            hook(classLoader)
        }
    }

    private fun Context.hook(classLoader: ClassLoader) {
        val supportedTypeClass = findClass(
            "com.samsung.android.shealthmonitor.util.CommonConstants\$SupportedType",
            classLoader
        )

        // ALL_SUPPORT 是 ordinal=0 的枚举值
        val allSupportType = supportedTypeClass.enumConstants?.firstOrNull()
            ?: run {
                XposedBridge.log("$TAG: Failed to get ALL_SUPPORT enum value")
                return
            }

        // 使用 DexKit 查找目标方法
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val method = bridge.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = supportedTypeClass.name
                    usingStrings("fake country not set")
                }
            }.singleOrNull()

            if (method == null) {
                XposedBridge.log("$TAG: Failed to find isSupportedCountry method")
                return
            }

            XposedBridge.log("$TAG: Hooking ${method.className}.${method.name}")

            XposedBridge.hookMethod(
                method.getMethodInstance(classLoader),
                XC_MethodReplacement.returnConstant(allSupportType)
            )
        }
    }
}