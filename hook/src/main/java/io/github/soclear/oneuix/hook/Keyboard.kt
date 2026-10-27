package io.github.soclear.oneuix.hook

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object Keyboard {
    private const val SHUANGPIN_LAYOUT = "shuangpin_qwerty"
    private const val LANGUAGE_CLASS = "com.samsung.android.honeyboard.base.languagepack.language.Language"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun unlockShuangpinLayout() {
        if (param.packageName != Package.KEYBOARD) return
        afterAttachTry {
            val config = getHookConfig { getHookConfigFromDexKit() }
            if (config == null) {
                xlog("Keyboard: no unique Shuangpin layout filter found; leaving keyboard unchanged")
                return@afterAttachTry
            }
            val method = DexMethod(config.layoutFilterMethod).getMethodInstance(classLoader)
            check(Modifier.isStatic(method.modifiers))
            xposedModule.hook(method).intercept { chain ->
                // Only bypass the layout filter. Keep all other layouts and region/engine policies intact.
                if (chain.args[1] == SHUANGPIN_LAYOUT) true else chain.proceed()
            }
            xlog("Keyboard: Shuangpin layout filter hooked: ${config.layoutFilterMethod}", priority = Log.INFO)
            try {
                KeyboardShuangpin594048.install(longVersionCode, classLoader)
            } catch (t: Throwable) {
                xlog("Keyboard: XT9 input adapter not installed; layout-only unlock remains active", t)
            }
        }
    }

    @Serializable
    private data class KeyboardHookConfig(
        override val versionCode: Long,
        val layoutFilterMethod: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): KeyboardHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            // Samsung Keyboard 5.9.40.48: Vg.a.b(Language, String). Do not depend on obfuscated names.
            val method = bridge.findMethod {
                matcher {
                    returnType = "boolean"
                    paramTypes(LANGUAGE_CLASS, "java.lang.String")
                    usingStrings(SHUANGPIN_LAYOUT)
                }
            }.singleOrNull() ?: return null
            return KeyboardHookConfig(
                versionCode = longVersionCode,
                layoutFilterMethod = method.toDexMethod().serialize(),
            )
        }
    }
}
