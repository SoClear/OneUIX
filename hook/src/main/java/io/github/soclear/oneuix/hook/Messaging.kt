package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object Messaging {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun unlockRcs() {
        if (param.packageName != Package.MESSAGING) return

        fun hookRcsSupportChecks(className: String, methodNames: Set<String>) {
            runCatching {
                param.classLoader.loadClass(className).declaredMethods.filter { method ->
                    method.name in methodNames &&
                        method.returnType == Boolean::class.javaPrimitiveType &&
                        (method.parameterCount == 0 ||
                            (method.parameterCount == 1 &&
                                method.parameterTypes[0] == Int::class.javaPrimitiveType))
                }.forEach { method ->
                    runCatching {
                        xposedModule.hook(method).intercept { true }
                    }.onFailure { xlog("RCS support hook unavailable: $method", it) }
                }
            }.onFailure { xlog("RCS support class unavailable: $className", it) }
        }

        hookRcsSupportChecks(
            "com.samsung.android.messaging.common.configuration.Feature",
            setOf("isRcsSupported", "getRcsCscEnabled")
        )
        hookRcsSupportChecks(
            "com.samsung.android.messaging.common.configuration.rcs.RcsFeatures",
            setOf("getRcsEnabled", "isDualRcsSupported")
        )

        // Keep the real IMS profile when local service-readiness or dual-SIM checks reject it.
        // Never invent a profile/version or report successful IMS registration.
        runCatching {
            val dataClass = param.classLoader.loadClass(
                $$"com.samsung.android.messaging.common.configuration.rcs.RcsFeatures$RcsEnableData"
            )
            val profileField = dataClass.getDeclaredField("mRcsProfile").apply {
                isAccessible = true
            }
            val isValid = dataClass.getDeclaredMethod("isValid")
            if (profileField.type == String::class.java &&
                isValid.returnType == Boolean::class.javaPrimitiveType
            ) {
                xposedModule.hook(isValid).intercept { chain ->
                    val original = chain.proceed()
                    original == true ||
                        !(profileField.get(chain.thisObject) as? String).isNullOrBlank()
                }
            }
        }.onFailure { xlog("RCS profile eligibility hook unavailable", it) }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun isSupportBlock() {
        if (param.packageName != Package.MESSAGING) return
        val featureClass = runCatching {
            param.classLoader.loadClass("com.samsung.android.messaging.common.configuration.Feature")
        }.getOrNull() ?: return
        listOf(
            "isSupportBlockNumber",
            "isSupportBlockPhrase",
            "isBlockNumberSettingEnable",
            "isSupportPhishingReport",
            "enableAlwaysSendSpamReport",
            "getEnableBotSpamReport",
            "getEnableSpamReport4Kor",
//            "isSupportAIFeature",
//            "isSupportAISpam",
            "isSupportMaliciousMessageDetection",
            "isSupportMaliciousMessageDetectionAndSpamBlocker",
            "isSupportBlockSpamByAi",
            "isSupportMcsAiSpamMessage",
            "isSupportMcsSpamOrMaliciousMessage",
            "isSupportSuggestAiSpamFilter",
            "isSupportSuggestMaliciousSpamFilter",
            "isSupportMcs",
        ).forEach { methodName ->
            try {
                featureClass.declaredMethods.filter { it.name == methodName }.forEach {
                    xposedModule.hook(it).intercept { true }
                }
            } catch (_: Throwable) {
            }
        }
    }
}
