package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

object SMCN {
    fun spoofPhoneStatusAsOfficial(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.processName != Package.SM_CN) return
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach
            try {
                val methodInstance = DexMethod(hookConfig.checkRootingConditionMethod).getMethodInstance(classLoader)
                hookMethod(methodInstance, returnConstant(1))
            } catch (t: Throwable) {
                XposedBridge.log(t)
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
                versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                checkRootingConditionMethod = checkRootingConditionMethodData.toDexMethod().serialize(),
            )
        }
    }
}
