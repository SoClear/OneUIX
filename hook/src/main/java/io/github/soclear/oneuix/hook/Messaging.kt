package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package

object Messaging {
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
