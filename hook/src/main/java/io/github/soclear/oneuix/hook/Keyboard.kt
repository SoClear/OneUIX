package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttachTry
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

object Keyboard {
    private const val XT9 = "com.samsung.android.honeyboard.predictionengine.core.xt9"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun unlockShuangpinLayout() = afterAttachTry(param.packageName == Package.KEYBOARD) {
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
            val layoutIndex = layoutFilter.paramTypeNames.indexOf("java.lang.String")
            xposedModule.hook(layoutFilter.getMethodInstance(classLoader)).intercept { chain ->
                if (chain.args[layoutIndex] == "shuangpin_qwerty") true else chain.proceed()
            }
            layoutFilter.callers.filter { it.isMethod }
                .forEach { xposedModule.deoptimize(it.getMethodInstance(classLoader)) }

            // Locate the feature controlling the Pinyin/Shuangpin keyboard database (0x0d00/0x0f00).
            // It may be read directly or through a boolean accessor, depending on the keyboard build.
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
            val featureField = feature.getFieldInstance(classLoader).apply { isAccessible = true }
            // Let HoneyBoard initialize its DI and feature table first; do not spoof the whole region.
            xposedModule.hookClassInitializer(featureField.declaringClass).intercept { chain ->
                chain.proceed().also { featureField.setBoolean(null, true) }
            }
            feature.readers.flatMap { listOf(it) + it.callers }
                .filter { it.isMethod }.distinctBy { it.descriptor }
                .forEach { xposedModule.deoptimize(it.getMethodInstance(classLoader)) }

            // Keep the host's key processing and geometry; restore only unlabelled Shuangpin symbol keys.
            val processKey = bridge.findMethod {
                matcher {
                    declaredClass = "$XT9.XT9KeyboardDatabase"
                    name = "processElseKey"
                    paramCount = 8
                }
            }.single().getMethodInstance(classLoader)
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
            processKey.declaringClass.declaredMethods.filter { it.name == "makeKeys" }
                .forEach { xposedModule.deoptimize(it) }
        }
    }

    private fun isShuangpinSymbol(code: Int) = code in 62000..62026 || code in 62032..62065
}
