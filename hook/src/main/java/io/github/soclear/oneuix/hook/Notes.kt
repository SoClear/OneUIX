package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package

object Notes {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportAllFeatures() {
        if (param.packageName != Package.NOTES) return
        val classLoader = param.classLoader
        fun findAndHookMethodCatching(className: String, methodName: String) {
            runCatching {
                xposedModule.hook(classLoader.loadClass(className).getDeclaredMethod(methodName))
                    .intercept { true }
            }
        }
        listOf(
            "isS25Devices", // 摘要风格
            "canUseAiSuggestion", // AI 建议
            "isAiDrawingT2IEnabled", // AI 文本转图像 (Text-to-Image，T2I)
            "isCreateImageEnabled", // 创建图像 (综合 S2I 和 T2I)
            "isEnabledScanText", // 扫描文本 (OCR)
        ).forEach {
            findAndHookMethodCatching(
                "com.samsung.android.support.senl.nt.composer.main.base.util.FeatureInfo",
                it
            )
        }
        // isAiDrawingS2IEnabled 用 Features.isSupportAiFunction() && Features.isSupportSketchToImageFunction();


        listOf(
            "isSupportAiFunction", // 检查是否支持通用 AI 功能
            "isSupportSketchToImageFunction", // AI 涂鸦转图像 (Sketch-to-Image，S2I)
            "isSupportNeuralTranslationFunction", // 支持神经机器翻译功能
            "isSupportSTTFunction", // 语音转文字 (Speech-to-Text - STT)
            "isSupportTextToImageFunction", // 文本转图像 (Text-to-Image)
            "isSupportImageTranslationFunction", // 图片内文本翻译功能
            "isSupportSTTSummaryGroupBySubject",
            "isSupportB2bLlmFunction",
            "isSupportB2bLvmFunction",
            "isSupportC2PAFunction",
            "isSupportDrawingAssistFunction",
            "isSupportSTTAutoDetectLanguage",
            "isSupportedDeviceMode",
        ).forEach {
            findAndHookMethodCatching(
                "com.samsung.android.support.senl.nt.base.common.ai.common.Features",
                it
            )
        }

        // 数学求解器
        findAndHookMethodCatching(
            "com.samsung.android.support.senl.nt.model.utils.FeatureUtils", "isMathHelperSupported"
        )
    }
}
