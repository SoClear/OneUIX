package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object StatusBar {
    fun setStatusBarPaddingDp(loadPackageParam: LoadPackageParam, left: Float?, right: Float?) {
        if (loadPackageParam.packageName != io.github.soclear.oneuix.common.Package.SYSTEMUI ||
            left == null && right == null
        ) {
            return
        }
        try {
            val clazz = findClass(
                "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout",
                loadPackageParam.classLoader
            )
            if (left != null) {
                findAndHookMethod(
                    clazz,
                    "calculateLeftPadding",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Int {
                            val inputProperties =
                                getObjectField(param.thisObject, "inputProperties")
                            val density = getObjectField(inputProperties, "density") as Float
                            return (left * density).roundToInt()
                        }
                    }
                )
            }
            if (right != null) {
                findAndHookMethod(
                    clazz,
                    "calculateRightPadding",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Int {
                            val inputProperties =
                                getObjectField(param.thisObject, "inputProperties")
                            val density = getObjectField(inputProperties, "density") as Float
                            return (right * density).roundToInt()
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setBatteryIconScale(
        loadPackageParam: LoadPackageParam,
        widthScale: Float?,
        heightScale: Float?
    ) {
        if (loadPackageParam.packageName != io.github.soclear.oneuix.common.Package.SYSTEMUI || widthScale == null && heightScale == null) return
        try {
            findAndHookMethod(
                "com.android.systemui.battery.BatteryMeterView",
                loadPackageParam.classLoader,
                "scaleBatteryMeterViewsLegacy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val mBatteryIconView =
                            getObjectField(param.thisObject, "mBatteryIconView") as ImageView
                        mBatteryIconView.layoutParams = mBatteryIconView.layoutParams.apply {
                            if (widthScale != null) {
                                width = (width * widthScale).roundToInt()
                            }
                            if (heightScale != null) {
                                height = (height * heightScale).roundToInt()
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideBatteryPercentageSign(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != io.github.soclear.oneuix.common.Package.SYSTEMUI ||
            Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return
        }
        val batterMeterFormat = "status_bar_settings_${
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "uniform_"
            else ""
        }battery_meter_format"
        resparam.res.setReplacement(Package.SYSTEMUI, "string", batterMeterFormat, "%d")
    }

    fun updateStatusBarClockEverySecond(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        // 每秒更新
        findAndHookMethod(
            "com.android.systemui.statusbar.policy.QSClockQuickStarHelper",
            loadPackageParam.classLoader,
            "updateSecondsClockHandler",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mSecondsHandler = getObjectField(param.thisObject, "mSecondsHandler")
                    if (mSecondsHandler != null) return
                    val looper = Looper.myLooper() ?: return
                    val handler = Handler(looper)
                    setObjectField(param.thisObject, "mSecondsHandler", handler)
                    val mSecondTick = getObjectField(param.thisObject, "mSecondTick") as Runnable
                    handler.post(mSecondTick)
                }
            }
        )

        // 数字字体等宽
        findAndHookMethod(
            "com.android.systemui.statusbar.policy.QSClockIndicatorViewController",
            loadPackageParam.classLoader,
            "onViewAttached",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val clockTextView = getObjectField(param.thisObject, "view") as TextView
                    clockTextView.fontFeatureSettings = "tnum"
                }
            }
        )
    }

    fun setStatusBarClockTextScale(loadPackageParam: LoadPackageParam, scale: Float) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        // The controller restores the system baseline size before this callback, so scaling
        // remains stable across density and font-scale changes instead of accumulating.
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val clockView = getObjectField(param.thisObject, "view") as TextView
                clockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, clockView.textSize * scale)
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController",
                loadPackageParam.classLoader,
                "onDensityOrFontScaleChanged",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setStatusBarClockFormat(loadPackageParam: LoadPackageParam, format: String) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
        setStatusBarClockText(loadPackageParam) {
            dateTimeFormatter.format(LocalDateTime.now())
        }
    }

    private fun setStatusBarClockText(loadPackageParam: LoadPackageParam, block: () -> String) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val callback = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                val clockTextView = param.thisObject as TextView
                val dateTime = block()
                clockTextView.text = dateTime
                clockTextView.contentDescription = dateTime
                return null
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView",
                loadPackageParam.classLoader,
                "notifyTimeChanged",
                "com.android.systemui.statusbar.policy.QSClockBellSound",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideSecureFolderStatusBarIcon(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == "managed_profile") {
                    param.result = null
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
                    loadPackageParam.classLoader,
                    "setIcon",
                    String::class.java,
                    "com.android.systemui.statusbar.phone.StatusBarIconHolder",
                    callback
                )
            } else {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
                    loadPackageParam.classLoader,
                    "setIcon",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    CharSequence::class.java,
                    callback
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun restoreBluetoothStatusBarIcon(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
                loadPackageParam.classLoader,
                "hideBySimplification",
                "com.android.systemui.statusbar.phone.ui.IconManager",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val slot = param.args[1] as? String ?: return
                        if (slot == "bluetooth" || slot == "bluetooth_connected") {
                            param.result = false
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun doubleTapStatusBarToSleep(loadPackageParam: LoadPackageParam) {
        val callback = object : XC_MethodHook() {
            var lastTapTime = 0L

            override fun beforeHookedMethod(param: MethodHookParam) {
                val event = param.args[0] as MotionEvent
                if (event.action != MotionEvent.ACTION_DOWN) {
                    return
                }
                val currentTime = System.nanoTime()
                val interval = currentTime - lastTapTime
                if (interval in 40_000_000L..300_000_000L) {
                    lastTapTime = 0L
                    val view = param.thisObject as View
                    lockScreen(view.context)
                    param.result = true
                } else {
                    lastTapTime = currentTime
                }
            }

            fun lockScreen(context: Context) {
                val powerManager = context.getSystemService(PowerManager::class.java)
                callMethod(powerManager, "goToSleep", SystemClock.uptimeMillis())
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                loadPackageParam.classLoader,
                "onTouchEvent",
                MotionEvent::class.java,
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideLockscreenStatusBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                loadPackageParam.classLoader,
                "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = View.GONE
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setCustomCarrierName(loadPackageParam: LoadPackageParam, carrierName: String) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.keyguard.CarrierTextManager",
                loadPackageParam.classLoader,
                "postToCallback",
                $$"com.android.keyguard.CarrierTextManager$CarrierTextCallbackInfo",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val carrierTextCallbackInfo = param.args[0] ?: return
                        runCatching { setObjectField(carrierTextCallbackInfo, "carrierText", carrierName) }
                        runCatching { setObjectField(carrierTextCallbackInfo, "carrierTextShort", carrierName) }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun addBatteryLevelText(
        loadPackageParam: LoadPackageParam,
        hidePercentSign: Boolean,
        hideChargingIcon: Boolean,
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || ONE_UI_VERSION < 70000) return
        val batteryMeterViewClass = findClassIfExists(
            "com.android.systemui.battery.BatteryMeterView",
            loadPackageParam.classLoader
        ) ?: return

        val viewId = View.generateViewId()

        try {
            findAndHookMethod(
                batteryMeterViewClass,
                "scaleBatteryMeterViewsLegacy",
                object : XC_MethodHook() {
                    @SuppressLint("SetTextI18n")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val batteryMeterView = param.thisObject as ViewGroup
                            var textView = batteryMeterView.findViewById<TextView>(viewId)
                            if (textView == null) {
                                textView = TextView(batteryMeterView.context).apply {
                                    id = viewId
                                    gravity = Gravity.CENTER
                                }
                                batteryMeterView.addView(
                                    textView, LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                )
                            }
                            val level = getIntField(batteryMeterView, "mLevel")
                            val percent = if (hidePercentSign) "$level" else "$level%"
                            val isCharging = callMethod(batteryMeterView, "isCharging") as Boolean
                            val suffix = if (isCharging && !hideChargingIcon) "\u26A1\uFE0E" else ""
                            textView.text = "$percent$suffix"
                            textView.setTextColor(getIntField(batteryMeterView, "mTextColor"))
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            hookAllMethods(batteryMeterViewClass, "updateColors", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val view = param.thisObject as ViewGroup
                        val textView = view.findViewById<TextView>(viewId) ?: return
                        textView.setTextColor(getIntField(view, "mTextColor"))
                    } catch (t: Throwable) {
                        XposedBridge.log(t)
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
