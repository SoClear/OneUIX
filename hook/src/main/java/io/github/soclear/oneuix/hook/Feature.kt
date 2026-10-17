package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.FeatureOverride
import io.github.soclear.oneuix.hook.util.xlog

/**
 * Answers the feature gates the app has turned on.
 *
 * Samsung keeps its feature switches in two tables, CSC features and floating features, and both
 * are fixed on the device. The module reaches a gate by hooking the getters that read the table and
 * handing back the value the app holds for that key.
 *
 * These are installed in every hooked process rather than for one app, because each app that offers
 * the feature reads the table itself, and a process has to be in the module's scope for a hook to
 * reach it at all.
 */
@SuppressLint("PrivateApi")
object Feature {
    private const val SEM_CSC_FEATURE = "com.samsung.android.feature.SemCscFeature"
    private const val SEM_FLOATING_FEATURE = "com.samsung.android.feature.SemFloatingFeature"

    /** The getters either table is read through. */
    private val GETTERS = setOf("getString", "getBoolean", "getInt", "getLong")

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun applyOverrides(overrides: List<FeatureOverride>) {
        val enabled = overrides.filter { it.enabled }
        if (enabled.isEmpty()) return

        hookTable(param.classLoader, SEM_CSC_FEATURE, FeatureOverride.CSC, enabled)
        hookTable(param.classLoader, SEM_FLOATING_FEATURE, FeatureOverride.FLOATING_FEATURE, enabled)
    }

    /** Hooks every getter of one table, so a key is answered whichever way it is asked for. */
    context(xposedModule: XposedModule)
    private fun hookTable(
        classLoader: ClassLoader,
        className: String,
        kind: Int,
        enabled: List<FeatureOverride>
    ) {
        val answers = enabled.filter { it.kind == kind }.associateBy { it.key }
        if (answers.isEmpty()) return

        try {
            classLoader.loadClass(className).declaredMethods
                .filter { method ->
                    method.name in GETTERS &&
                        method.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        answers[chain.args.firstOrNull()]?.let { answer(it, method.returnType) }
                            ?: chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    /**
     * The value held for a key, in the shape the getter that asked for it returns. A getter that
     * cannot express the value is left to the ROM.
     */
    private fun answer(override: FeatureOverride, returnType: Class<*>): Any? = when (returnType) {
        String::class.java -> override.value
        Boolean::class.javaPrimitiveType -> override.value.asBoolean()
        Int::class.javaPrimitiveType -> override.value.trim().toIntOrNull()
        Long::class.javaPrimitiveType -> override.value.trim().toLongOrNull()
        Array<String>::class.java -> override.value
            .split(',', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()

        else -> null
    }

    private fun String.asBoolean(): Boolean {
        val trimmed = trim()
        return trimmed.equals("true", ignoreCase = true) || trimmed == "1"
    }
}
