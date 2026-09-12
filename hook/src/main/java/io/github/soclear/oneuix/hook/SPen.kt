package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object SPen {
    fun switchTranslateSource(loadPackageParam: LoadPackageParam, useGoogle: Boolean) {
        if (loadPackageParam.packageName != Package.TRANSLATION) return

        val classLoader = loadPackageParam.classLoader

        try {
            val validationClass = findClass(
                "com.samsung.sdk.clickstreamanalytics.internal.policy.Validation",
                classLoader
            )

            findAndHookMethod(
                validationClass,
                "isChinaModel",
                returnConstant(false)
            )

            findAndHookMethod(
                validationClass,
                "getCountryCode",
                String::class.java,
                returnConstant("CN")
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            val cscFeatureClass = findClass(
                "com.samsung.android.feature.SemCscFeature",
                classLoader
            )

            val callback = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? String ?: return
                    if (key.contains("translate", ignoreCase = true) ||
                        key.contains("SPen_ConfigDefTranslatorSolution", ignoreCase = true) ||
                        key == "DefaultCscFeature_Spen_Translation" ||
                        key == "CscFeature_Spen_Translation"
                    ) {
                        param.result = if (useGoogle) "GOOGLE" else "BAIDU"
                    }
                }
            }

            findAndHookMethod(
                cscFeatureClass,
                "getString",
                String::class.java,
                callback
            )

            findAndHookMethod(
                cscFeatureClass,
                "getString",
                String::class.java,
                String::class.java,
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
