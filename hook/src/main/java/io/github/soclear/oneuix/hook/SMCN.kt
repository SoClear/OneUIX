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

object SMCN {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun spoofPhoneStatusAsOfficial() {
        if (param.packageName != Package.SM_CN) return
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach
            try {
                val methodInstance = DexMethod(hookConfig.checkRootingConditionMethod).getMethodInstance(classLoader)
                xposedModule.hook(methodInstance).intercept { 1 }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    @Serializable
    private data class SMCNHookConfig(
        override val versionCode: Long,
        val checkRootingConditionMethod: String
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): SMCNHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val exclusions = listOf(
                "android",
                "androidx",
                "cleanwx",
                "clear",
                "com",
                "kotlin",
                "kotlinx",
                "mobilesmart",
                "okhttp3",
                "retrofit2",
            )
            val checkRootingConditionMethodData = bridge.findMethod {
                excludePackages(exclusions)
                matcher {
                    usingStrings(
                        "ro.boot.flash.locked",
                        "device status : ",
                        "rooting:su located at : "
                    )
                }
            }.singleOrNull() ?: return null
            return SMCNHookConfig(
                versionCode = longVersionCode,
                checkRootingConditionMethod = checkRootingConditionMethodData.toDexMethod().serialize(),
            )
        }
    }
}
