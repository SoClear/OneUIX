package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object Keyboard {
    private const val XT9 = "com.samsung.android.honeyboard.predictionengine.core.xt9"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun unlockShuangpinLayout() = afterAttachTry(param.packageName == Package.KEYBOARD) {
        val config = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttachTry
        val layoutFilter = DexMethod(config.layoutFilterMethod).getMethodInstance(classLoader)
        val layoutIndex = layoutFilter.parameterTypes.indexOf(String::class.java)
        xposedModule.hook(layoutFilter).intercept { chain ->
            if (chain.args[layoutIndex] == "shuangpin_qwerty") true else chain.proceed()
        }
        val featureField = DexField(config.shuangpinFeatureField).getFieldInstance(classLoader)
            .apply { isAccessible = true }
        // Let HoneyBoard initialize its DI and feature table first; do not spoof the whole region.
        xposedModule.hookClassInitializer(featureField.declaringClass).intercept { chain ->
            chain.proceed().also { featureField.setBoolean(null, true) }
        }

        // Keep the host's key processing and geometry; restore only unlabelled Shuangpin symbol keys.
        val processKey = DexMethod(config.processKeyMethod).getMethodInstance(classLoader)
        val source = processKey.parameterTypes[0]
        val codesField = source.declaredFields.single { it.type == IntArray::class.java }
            .apply { isAccessible = true }
        val labelField = source.declaredFields.single { it.type == CharSequence::class.java }
            .apply { isAccessible = true }
        val keyClass = classLoader.loadClass("$XT9.XT9KeyboardDatabase\$XT9Key")
        val constructor = keyClass.declaredConstructors.single {
            it.parameterCount == 10 && it.parameterTypes[0] == IntArray::class.java
        }.apply { isAccessible = true }
        val keyType = keyClass.getDeclaredField("type").apply { isAccessible = true }
        val bounds = listOf("x", "y", "width", "height").map { name ->
            keyClass.getDeclaredField(name).apply { isAccessible = true }
        }
        xposedModule.hook(processKey).intercept { chain ->
            @Suppress("UNCHECKED_CAST")
            val keys = chain.args[1] as MutableList<Any>
            val size = keys.size
            val result = chain.proceed()
            if (chain.args[3] == true || keys.size == size) return@intercept result
            val sourceKey = chain.args[0]
            val codes = codesField.get(sourceKey) as IntArray
            val label = labelField.get(sourceKey) as? CharSequence
            val key = keys.last()
            if (label.isNullOrEmpty() && keyType.getInt(key) == -1 && codes.any(::isShuangpinSymbol)) {
                keys[keys.lastIndex] = constructor.newInstance(
                    codes, 0, bounds[0].getInt(key), bounds[1].getInt(key),
                    bounds[2].getInt(key), bounds[3].getInt(key),
                    chain.args[4], chain.args[2], chain.args[5], chain.args[6]
                )
            }
            result
        }
        config.deoptimizeMethods.forEach {
            xposedModule.deoptimize(DexMethod(it).getMethodInstance(classLoader))
        }
    }

    @Serializable
    private data class KeyboardHookConfig(
        override val versionCode: Long,
        val layoutFilterMethod: String,
        val shuangpinFeatureField: String,
        val processKeyMethod: String,
        val deoptimizeMethods: List<String>,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): KeyboardHookConfig {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val layoutFilter = bridge.findMethod {
                matcher {
                    returnType = "boolean"
                    paramCount = 2
                    usingStrings("shuangpin_qwerty")
                }
            }.single {
                it.paramTypeNames.toSet() == setOf(
                    "java.lang.String",
                    "com.samsung.android.honeyboard.base.languagepack.language.Language"
                )
            }
            // The Pinyin/Shuangpin database feature may be read directly or through an accessor.
            val keyboardId = bridge.findMethod {
                searchPackages(XT9)
                matcher {
                    returnType = "int"
                    paramTypes("int")
                    usingNumbers(225, 0x0d00, 0x0f00)
                }
            }.single()
            val feature = (keyboardId.usingFields + keyboardId.invokes
                .filter { it.returnTypeName == "boolean" && it.paramCount == 0 }
                .flatMap { it.usingFields })
                .map { it.field }
                .filter { it.typeName == "boolean" && Modifier.isStatic(it.modifiers) }
                .distinctBy { it.descriptor }
                .single()
            val processKey = bridge.findMethod {
                matcher {
                    declaredClass = "$XT9.XT9KeyboardDatabase"
                    name = "processElseKey"
                    paramCount = 8
                }
            }.single()
            val deoptimizeMethods = (layoutFilter.callers + processKey.callers +
                feature.readers.flatMap { listOf(it) + it.callers })
                .filter { it.isMethod }.distinctBy { it.descriptor }
            return KeyboardHookConfig(
                versionCode = longVersionCode,
                layoutFilterMethod = layoutFilter.toDexMethod().serialize(),
                shuangpinFeatureField = feature.toDexField().serialize(),
                processKeyMethod = processKey.toDexMethod().serialize(),
                deoptimizeMethods = deoptimizeMethods.map { it.toDexMethod().serialize() },
            )
        }
    }

    private fun isShuangpinSymbol(code: Int) = code in 62000..62026 || code in 62032..62065
}
