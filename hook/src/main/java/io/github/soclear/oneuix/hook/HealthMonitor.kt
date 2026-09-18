package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object HealthMonitor {
    private const val SUPPORTED_TYPE_CLASS = $$"com.samsung.android.shealthmonitor.util.CommonConstants$SupportedType"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun bypassCountryCheck() {
        if (param.packageName != Package.HEALTH_MONITOR) {
            return
        }
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach
            try {
                val supportedTypeClass = classLoader.loadClass(SUPPORTED_TYPE_CLASS)

                // 使用 准确的 ALL_SUPPORT 为后续可能的血压功能提前准备
                // 开启血压功能需要手表区域检验通过
                val allSupportType = supportedTypeClass.enumConstants
                    ?.filterIsInstance<Enum<*>>()
                    ?.firstOrNull { it.name == "ALL_SUPPORT" }
                    ?: return@afterAttach

                val method = DexMethod(hookConfig.isSupportedCountryMethod).getMethodInstance(classLoader)
                xposedModule.hook(method).intercept { allSupportType }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    @Serializable
    data class HealthMonitorHookConfig(
        override val versionCode: Long,
        val isSupportedCountryMethod: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): HealthMonitorHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val supportedTypeClass = bridge.findClass {
                matcher {
                    superClass = "java.lang.Enum"
                    usingStrings("ALL_SUPPORT", "MCC_NOT_SUPPORT", "CSC_NOT_SUPPORT")
                }
            }.singleOrNull() ?: return null

            val isSupportedCountryMethod = bridge.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = supportedTypeClass.name
                    usingStrings("fake country not set")
                }
            }.singleOrNull() ?: return null

            return HealthMonitorHookConfig(
                versionCode = longVersionCode,
                isSupportedCountryMethod = isSupportedCountryMethod.toDexMethod().serialize()
            )
        }
    }
}
