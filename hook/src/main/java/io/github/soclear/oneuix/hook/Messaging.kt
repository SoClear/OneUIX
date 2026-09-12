package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object Messaging {
    fun isSupportBlock(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.MESSAGING) return
        val featureClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.common.configuration.Feature",
            loadPackageParam.classLoader
        ) ?: return
        val returnTrue = XC_MethodReplacement.returnConstant(true)
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
        ).forEach {
            try {
                XposedBridge.hookAllMethods(featureClass, it, returnTrue)
            } catch (_: Throwable) {
            }
        }
    }
}
