package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object SPen {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun switchTranslateSource(useGoogle: Boolean) {
        if (param.packageName != Package.TRANSLATION) return

        val classLoader = param.classLoader

        try {
            val validationClass = classLoader.loadClass(
                "com.samsung.sdk.clickstreamanalytics.internal.policy.Validation"
            )

            xposedModule.hook(
                validationClass.getDeclaredMethod("isChinaModel")
            ).intercept { false }

            xposedModule.hook(
                validationClass.getDeclaredMethod("getCountryCode", String::class.java)
            ).intercept { "CN" }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val cscFeatureClass = classLoader.loadClass(
                "com.samsung.android.feature.SemCscFeature"
            )

            xposedModule.hook(
                cscFeatureClass.getDeclaredMethod("getString", String::class.java)
            ).intercept { chain ->
                val key = chain.args[0] as? String
                if (key != null &&
                    (key.contains("translate", ignoreCase = true) ||
                        key.contains("SPen_ConfigDefTranslatorSolution", ignoreCase = true) ||
                        key == "DefaultCscFeature_Spen_Translation" ||
                        key == "CscFeature_Spen_Translation")
                ) {
                    if (useGoogle) "GOOGLE" else "BAIDU"
                } else {
                    chain.proceed()
                }
            }

            xposedModule.hook(
                cscFeatureClass.getDeclaredMethod("getString", String::class.java, String::class.java)
            ).intercept { chain ->
                val key = chain.args[0] as? String
                if (key != null &&
                    (key.contains("translate", ignoreCase = true) ||
                        key.contains("SPen_ConfigDefTranslatorSolution", ignoreCase = true) ||
                        key == "DefaultCscFeature_Spen_Translation" ||
                        key == "CscFeature_Spen_Translation")
                ) {
                    if (useGoogle) "GOOGLE" else "BAIDU"
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
