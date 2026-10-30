package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object SamsungHealth {
    context(xposedModule: XposedModule)
    fun hideDiscover() = afterAttachTry {
        val config = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttachTry
        listOf(config.isDiscoverPlusSupported, config.isPromotionSupported).forEach {
            xposedModule.hook(DexMethod(it).getMethodInstance(classLoader)).intercept { false }
        }
    }

    @Serializable
    private data class SamsungHealthHookConfig(
        override val versionCode: Long,
        val isDiscoverPlusSupported: String,
        val isPromotionSupported: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): SamsungHealthHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val method = bridge.findMethod {
                matcher {
                    usingEqStrings("isDiscoverPlusSupported()")
                    returnType = "boolean"
                    paramCount = 0
                }
            }.singleOrNull() ?: return null
            val promotionSupported = bridge.findClass {
                matcher {
                    usingEqStrings("SHEALTH#PromotionManager", "restorePromotions started")
                }
            }.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = "boolean"
                    paramCount = 0
                }
            }.singleOrNull() ?: return null
            return SamsungHealthHookConfig(
                versionCode = longVersionCode,
                isDiscoverPlusSupported = method.toDexMethod().serialize(),
                isPromotionSupported = promotionSupported.toDexMethod().serialize(),
            )
        }
    }
}
