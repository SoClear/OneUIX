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
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
object StatusBar {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setStatusBarPaddingDp(left: Float?, right: Float?) {
        if (param.packageName != Package.SYSTEMUI ||
            (left == null && right == null)
        ) {
            return
        }
        try {
            afterAttach {
                val clazz =
                    classLoader.loadClass("com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout")
                if (left != null) {
                    val method = clazz.getDeclaredMethod("calculateLeftPadding")
                    xposedModule.hook(method).intercept { chain ->
                        val inputProperties = chain.thisObject.reflect["inputProperties"]
                        val density = inputProperties?.reflect?.get("density") as? Float ?: 1f
                        (left * density).roundToInt()
                    }
                }
                if (right != null) {
                    val method = clazz.getDeclaredMethod("calculateRightPadding")
                    xposedModule.hook(method).intercept { chain ->
                        val inputProperties = chain.thisObject.reflect["inputProperties"]
                        val density = inputProperties?.reflect?.get("density") as? Float ?: 1f
                        (right * density).roundToInt()
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setBatteryIconScale(
        widthScale: Float?,
        heightScale: Float?
    ) {
        if (param.packageName != Package.SYSTEMUI || (widthScale == null && heightScale == null)) return
        try {
            val clazz = param.classLoader.loadClass("com.android.systemui.battery.BatteryMeterView")
            val method = clazz.getDeclaredMethod("scaleBatteryMeterViewsLegacy")
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    val mBatteryIconView = chain.thisObject.reflect["mBatteryIconView"] as? ImageView
                    if (mBatteryIconView != null) {
                        mBatteryIconView.layoutParams = mBatteryIconView.layoutParams.apply {
                            if (widthScale != null) {
                                width = (width * widthScale).roundToInt()
                            }
                            if (heightScale != null) {
                                height = (height * heightScale).roundToInt()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }


    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideBatteryPercentageSign() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return
        }
        afterAttach {
            try {
                val batterMeterFormat = "status_bar_settings_${
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "uniform_"
                    else ""
                }battery_meter_format"

                @SuppressLint("DiscouragedApi")
                val targetId = resources.getIdentifier(batterMeterFormat, "string", Package.SYSTEMUI)
                if (targetId != 0) {
                    val resourcesClass = param.classLoader.loadClass("android.content.res.Resources")
                    resourcesClass.declaredMethods
                        .filter { it.name == "getString" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType }
                        .forEach { method ->
                            xposedModule.hook(method).intercept { chain ->
                                if (chain.args.firstOrNull() == targetId) "%d" else chain.proceed()
                            }
                        }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun updateStatusBarClockEverySecond() {
        if (param.packageName != Package.SYSTEMUI) return
        // 每秒更新
        try {
            val helperClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockQuickStarHelper"
            )
            val method = helperClass.getDeclaredMethod("updateSecondsClockHandler")
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    val mSecondsHandler = chain.thisObject.reflect["mSecondsHandler"]
                    if (mSecondsHandler == null) {
                        val looper = Looper.myLooper()
                        if (looper != null) {
                            val handler = Handler(looper)
                            chain.thisObject.reflect["mSecondsHandler"] = handler
                            val mSecondTick = chain.thisObject.reflect["mSecondTick"] as? Runnable
                            if (mSecondTick != null) {
                                handler.post(mSecondTick)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 数字字体等宽
        try {
            val controllerClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController"
            )
            val onViewAttachedMethod = controllerClass.getDeclaredMethod("onViewAttached")
            xposedModule.hook(onViewAttachedMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val clockTextView = chain.thisObject.reflect["view"] as? TextView
                    clockTextView?.fontFeatureSettings = "tnum"
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setStatusBarClockTextScale(scale: Float) {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            val controllerClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController"
            )
            val method = controllerClass.getDeclaredMethod("onDensityOrFontScaleChanged")
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    val clockView = chain.thisObject.reflect["view"] as? TextView
                    clockView?.setTextSize(TypedValue.COMPLEX_UNIT_PX, clockView.textSize * scale)
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setStatusBarClockFormat(format: String) {
        if (param.packageName != Package.SYSTEMUI) return
        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
        setStatusBarClockText {
            dateTimeFormatter.format(LocalDateTime.now())
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun setStatusBarClockText(block: () -> String) = afterAttach {
        if (param.packageName != Package.SYSTEMUI) return@afterAttach
        try {
            val clockClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView"
            )
            val qsClockBellSoundClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockBellSound"
            )
            val method = clockClass.getDeclaredMethod("notifyTimeChanged", qsClockBellSoundClass)
            xposedModule.hook(method).intercept { chain ->
                val clockTextView = chain.thisObject as? TextView
                val dateTime = block()
                clockTextView?.text = dateTime
                clockTextView?.contentDescription = dateTime
                null
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideSecureFolderStatusBarIcon() {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val controllerImplClass = param.classLoader.loadClass(
                    "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl"
                )
                val holderClass = param.classLoader.loadClass(
                    "com.android.systemui.statusbar.phone.StatusBarIconHolder"
                )
                val setIconMethod = controllerImplClass.getDeclaredMethod(
                    "setIcon",
                    String::class.java,
                    holderClass
                )
                xposedModule.hook(setIconMethod).intercept { chain ->
                    val slot = chain.args[0] as? String
                    if (slot == "secure_folder") {
                        null
                    } else {
                        chain.proceed()
                    }
                }
            } else {
                val controllerImplClass = param.classLoader.loadClass(
                    "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl"
                )
                val setIconMethod = controllerImplClass.getDeclaredMethod(
                    "setIcon",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    CharSequence::class.java
                )
                xposedModule.hook(setIconMethod).intercept { chain ->
                    val slot = chain.args[0] as? String
                    if (slot == "secure_folder") {
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun restoreBluetoothStatusBarIcon() {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            val controllerImplClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl"
            )
            val iconManagerClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.phone.ui.IconManager"
            )
            val method = controllerImplClass.getDeclaredMethod(
                "hideBySimplification",
                iconManagerClass,
                String::class.java
            )
            xposedModule.hook(method).intercept { chain ->
                val slot = chain.args.getOrNull(1) as? String
                if (slot == "bluetooth" || slot == "bluetooth_connected") {
                    false
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun doubleTapStatusBarToSleep() = afterAttach {
        var lastTapTime = 0L

        fun lockScreen(context: Context) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            powerManager?.reflect?.call("goToSleep", SystemClock.uptimeMillis())
        }

        try {
            val viewClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView"
            )
            val method = viewClass.getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
            xposedModule.hook(method).intercept { chain ->
                val event = chain.args[0] as MotionEvent
                if (event.action != MotionEvent.ACTION_DOWN) {
                    chain.proceed()
                } else {
                    val currentTime = System.nanoTime()
                    val interval = currentTime - lastTapTime
                    if (interval in 40_000_000L..300_000_000L) {
                        lastTapTime = 0L
                        val view = chain.thisObject as View
                        lockScreen(view.context)
                        true
                    } else {
                        lastTapTime = currentTime
                        chain.proceed()
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideLockscreenStatusBar() {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            val viewClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView"
            )
            val method = viewClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            xposedModule.hook(method).intercept { chain ->
                val newArgs = chain.args.toTypedArray()
                newArgs[0] = View.GONE
                chain.proceed(newArgs)
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setCustomCarrierName(carrierName: String) {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            val managerClass = param.classLoader.loadClass(
                "com.android.keyguard.CarrierTextManager"
            )
            val callbackInfoClass = param.classLoader.loadClass(
                $$"com.android.keyguard.CarrierTextManager$CarrierTextCallbackInfo"
            )
            val method = managerClass.getDeclaredMethod("postToCallback", callbackInfoClass)
            xposedModule.hook(method).intercept { chain ->
                val carrierTextCallbackInfo = chain.args[0]
                if (carrierTextCallbackInfo != null) {
                    runCatching { carrierTextCallbackInfo.reflect["carrierText"] = carrierName }
                    runCatching { carrierTextCallbackInfo.reflect["carrierTextShort"] = carrierName }
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    @SuppressLint("SetTextI18n")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun addBatteryLevelText(
        hidePercentSign: Boolean,
        hideChargingIcon: Boolean,
    ) {
        if (param.packageName != Package.SYSTEMUI || ONE_UI_VERSION < 70000) return
        val batteryMeterViewClass = runCatching {
            param.classLoader.loadClass("com.android.systemui.battery.BatteryMeterView")
        }.getOrNull() ?: return

        val viewId = View.generateViewId()

        try {
            val scaleMethod = batteryMeterViewClass.getDeclaredMethod("scaleBatteryMeterViewsLegacy")
            xposedModule.hook(scaleMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val batteryMeterView = chain.thisObject as ViewGroup
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
                    val level = batteryMeterView.reflect["mLevel"] as? Int ?: 0
                    val percent = if (hidePercentSign) "$level" else "$level%"
                    val isCharging = batteryMeterView.reflect.call("isCharging") as? Boolean ?: false
                    val suffix = if (isCharging && !hideChargingIcon) "\u26A1\uFE0E" else ""
                    textView.text = "$percent$suffix"
                    val textColor = batteryMeterView.reflect["mTextColor"] as? Int ?: 0
                    textView.setTextColor(textColor)
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            batteryMeterViewClass.declaredMethods
                .filter { it.name == "updateColors" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val view = chain.thisObject as ViewGroup
                            val textView = view.findViewById<TextView>(viewId)
                            val textColor = view.reflect["mTextColor"] as? Int ?: 0
                            textView?.setTextColor(textColor)
                        } catch (t: Throwable) {
                            xlog(t)
                        }
                        result
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
