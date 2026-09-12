package io.github.soclear.oneuix.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.XposedHelpers.setStaticObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object Browser {
    fun showMorePlaybackSpeeds(lpparam: LoadPackageParam) {
        if (lpparam.packageName != Package.BROWSER) {
            return
        }
        try {
            try {
                // Restore visibility of playback speed feature
                findAndHookMethod(
                    "com.sec.android.app.sbrowser.common.device.setting_preference.SettingPreference",
                    lpparam.classLoader,
                    "getPlaybackRateViewVisibility",
                    XC_MethodReplacement.returnConstant(true)
                )

                findAndHookMethod(
                    $$"com.sec.android.app.sbrowser.media.common.MediaFeatureGlobalConfigUtils$Companion",
                    lpparam.classLoader,
                    "isPlaybackSpeedEnabled",
                    Context::class.java,
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }

            val playbackRateViewClass = findClassIfExists(
                "${Package.BROWSER}.media.player.fullscreen.view.MPFullScreenPlaybackRateView",
                lpparam.classLoader
            ) ?: return
            val sPlaybackRates = doubleArrayOf(
                0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 4.0
            )
            findAndHookMethod(
                playbackRateViewClass,
                "init",
                LinearLayout::class.java,
                object : XC_MethodHook() {

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            setPlaybackRates()
                            setPlaybackRateViewWidth(param)
                            addMoreSpeeds(param)
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }

                    fun setPlaybackRates() = setStaticObjectField(
                        playbackRateViewClass, "sPlaybackRates", sPlaybackRates
                    )

                    fun addMoreSpeeds(param: MethodHookParam) {
                        val radioGroup = getObjectField(
                            param.thisObject,
                            "mPlaybackSpeedRadioGroup"
                        ) as? RadioGroup ?: return
                        val radioButton = radioGroup.getChildAt(0) as? RadioButton ?: return
                        radioGroup.addView(createRadioButton("3.0", radioButton))
                        radioGroup.addView(createRadioButton("4.0", radioButton))
                    }

                    fun createRadioButton(text: String, template: RadioButton): RadioButton {
                        return RadioButton(template.context).apply {
                            id = View.generateViewId()
                            this.text = text
                            textSize = 12f
                            setTextColor(template.currentTextColor)
                            buttonDrawable = template.buttonDrawable
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                            typeface = template.typeface
                            setPadding(0, 0, 0, 0)
                            layoutParams = RadioGroup.LayoutParams(template.layoutParams).apply {
                                weight = 1f
                                setMargins(0, 0, 0, 0)
                            }
                        }
                    }

                    fun setPlaybackRateViewWidth(param: MethodHookParam) {
                        val view = getObjectField(
                            param.thisObject,
                            "mPlaybackRateView"
                        ) as? View ?: return

                        val density = view.resources.displayMetrics.density
                        view.layoutParams = view.layoutParams.apply {
                            width = (380 * density).toInt()
                        }
                    }
                }
            )


            findAndHookMethod(
                playbackRateViewClass,
                "setInitialSpeed",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        try {
                            val mController = getObjectField(param.thisObject, "mController")
                            val speed = callMethod(mController, "getPlaybackRate") as Double
                            var index = sPlaybackRates.indexOfFirst { it == speed }
                            // Default to 1.0x
                            if (index == -1) index = 3

                            val radioGroup = getObjectField(
                                param.thisObject, "mPlaybackSpeedRadioGroup"
                            ) as? RadioGroup ?: return null
                            val radioButton =
                                radioGroup.getChildAt(index) as? RadioButton ?: return null

                            callMethod(mController, "setPlaybackRate", speed)
                            setObjectField(param.thisObject, "mCurrentSpeed", radioButton)
                            radioButton.isChecked = true
                            callMethod(
                                param.thisObject,
                                "highlightSelectedSpeedButton",
                                radioButton
                            )
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                        return null
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setCountryIsoCode(loadPackageParam: LoadPackageParam, code: String) {
        if (loadPackageParam.packageName != Package.BROWSER) return
        try {
            findAndHookMethod(
                "com.sec.android.app.sbrowser.common.application.AppInfo",
                loadPackageParam.classLoader,
                "isCnApk",
                XC_MethodReplacement.returnConstant(code == "CN")
            )

            val callback = XC_MethodReplacement.returnConstant(code)

            findAndHookMethod(
                "com.sec.android.app.sbrowser.common.device.CountryUtil",
                loadPackageParam.classLoader,
                "getCountryIsoCode",
                callback
            )

            findAndHookMethod(
                "com.sec.android.app.sbrowser.common.device.SystemProperties",
                loadPackageParam.classLoader,
                "getCountryCodeintoLocaleForGED",
                callback
            )

            findAndHookMethod(
                "com.sec.android.app.sbrowser.common.device.SystemProperties",
                loadPackageParam.classLoader,
                "getCscCountryIsoCode",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun redirectCustomTab(loadPackageParam: LoadPackageParam) {
        findAndHookMethod(
            "com.sec.android.app.sbrowser.customtabs.CustomTabActivity",
            loadPackageParam.classLoader,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val originalIntent = activity.intent ?: return
                    val uri = originalIntent.data ?: return
                    val scheme = uri.scheme?.lowercase()
                    if (scheme != "http" && scheme != "https") {
                        return
                    }
                    val cleanIntent = Intent(originalIntent).apply {
                        component = null
                        `package` = loadPackageParam.packageName
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        extras?.keySet()?.filter { key ->
                            key.startsWith("androidx.browser.customtabs.extra.") ||
                                    key.startsWith("android.support.customtabs.extra.") ||
                                    key.startsWith("org.chromium.chrome.browser.customtabs.")
                        }?.forEach(::removeExtra)
                        putExtra("com.android.browser.application_id", loadPackageParam.packageName)
                        putExtra("create_new_tab", true)
                    }
                    activity.startActivity(cleanIntent)
                    activity.finish()
                    originalIntent.data = null
                }
            }
        )
    }
}
