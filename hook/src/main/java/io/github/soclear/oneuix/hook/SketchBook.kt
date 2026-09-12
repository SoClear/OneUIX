package io.github.soclear.oneuix.hook

import android.content.Context
import android.graphics.Bitmap
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.soclear.oneuix.common.Package

object SketchBook {
    fun noAIWatermark(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (loadPackageParam.processName != Package.SKETCH_BOOK) return
        try {
            // 去掉实际保存的水印
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.sketchbook.common.utils.watermark.WatermarkUtils",
                loadPackageParam.classLoader,
                "combineWatermark",
                Context::class.java,
                Bitmap::class.java,
                Bitmap::class.java,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        // originBitmap
                        return param.args[1]
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        try {
            // 去掉预览水印
            val watermarkOverlayUtilsClass = XposedHelpers.findClass(
                "com.samsung.android.app.sketchbook.common.utils.watermark.WatermarkOverlayUtils",
                loadPackageParam.classLoader
            )
            XposedBridge.hookAllMethods(
                watermarkOverlayUtilsClass,
                "setupWatermarkOverlay",
                XC_MethodReplacement.DO_NOTHING
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
