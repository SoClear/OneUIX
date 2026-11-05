package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

object PhotoRetouching {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun noAIWatermark() = afterAttachTry {
        val hookConfig = getHookConfig { getHookConfigFromDexKit() }
        if (hookConfig != null) {
            try {
                val methodInstance =
                    DexMethod(hookConfig.saveWatermarkMethod).getMethodInstance(classLoader)
                xposedModule.hook(methodInstance).intercept { null }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    @Serializable
    private data class PhotoRetouchingHookConfig(
        override val versionCode: Long,
        val saveWatermarkMethod: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): PhotoRetouchingHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val saveWatermarkMethod = bridge.findMethod {
                matcher {
                    usingStrings = listOf(
                        "getWatermarkBitmap : requiredSize = ",
                        "saveWatermark : canvas shortAxis = ",
                    )
                    returnType = "void"
                    paramTypes("android.graphics.Canvas", "android.content.Context")
                }
            }.singleOrNull() ?: return null

            return PhotoRetouchingHookConfig(
                versionCode = longVersionCode,
                saveWatermarkMethod = saveWatermarkMethod.toDexMethod().serialize(),
            )
        }
    }
}
