package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
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
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
object StatusBar {
    context(xposedModule: XposedModule)
    fun alignContentToTop() = afterAttachTry {
        val clazz = classLoader.loadClass("com.android.systemui.statusbar.phone.IndicatorBasicGardener")
        val method = clazz.declaredMethods.single { it.name == "updateGarden" }
        xposedModule.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                chain.thisObject.reflect["gardenName"] == "PhoneStatusBarViewController"
            ) {
                val params = chain.thisObject.reflect.call("getCameraTopMarginContainerMarginLayoutParams")
                    as? ViewGroup.MarginLayoutParams
                params?.topMargin = 0
            }
            result
        }
    }

    context(xposedModule: XposedModule)
    fun setStatusBarPaddingDp(left: Float?, right: Float?) = afterAttachTry(left != null || right != null) {
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

    context(xposedModule: XposedModule)
    fun setBatteryIconScale(
        widthScale: Float?,
        heightScale: Float?
    ) = afterAttachTry(widthScale != null || heightScale != null) {
        if (ONE_UI_VERSION >= 90000) {
            val clazz = classLoader.loadClass("androidx.compose.ui.platform.AbstractComposeView")
            xposedModule.hook(clazz.declaredMethods.single { it.name == "onLayout" }).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                val parent = view.parent as? ViewGroup
                // The Compose battery replaces a hidden BatteryMeterView in the same container.
                if (parent != null && (0 until parent.childCount).any {
                        parent.getChildAt(it).javaClass.name == "com.android.systemui.battery.BatteryMeterView"
                    }) {
                    view.scaleX = widthScale ?: 1f
                    view.scaleY = heightScale ?: 1f
                    view.pivotX = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) view.width.toFloat() else 0f
                    view.pivotY = view.height / 2f
                    val params = view.layoutParams as ViewGroup.MarginLayoutParams
                    val margin = (view.width * (view.scaleX - 1f)).roundToInt()
                    if (params.marginEnd != margin) {
                        params.marginEnd = margin
                        view.layoutParams = params
                    }
                }
                result
            }
            return@afterAttachTry
        }
        val clazz = classLoader.loadClass("com.android.systemui.battery.BatteryMeterView")
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
    }


    context(xposedModule: XposedModule)
    fun hideBatteryPercentageSign() = afterAttachTry(Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val batterMeterFormat = "status_bar_settings_${
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "uniform_"
            else ""
        }battery_meter_format"

        @SuppressLint("DiscouragedApi")
        val targetId = resources.getIdentifier(batterMeterFormat, "string", Package.SYSTEMUI)
        if (targetId != 0) {
            val resourcesClass = classLoader.loadClass("android.content.res.Resources")
            // Keep the formatting overload intact so it can substitute the battery level.
            val method = resourcesClass.getDeclaredMethod("getString", Int::class.javaPrimitiveType)
            xposedModule.hook(method).intercept { chain ->
                if (chain.args.firstOrNull() == targetId) "%d" else chain.proceed()
            }
        }
    }

    context(xposedModule: XposedModule)
    fun updateStatusBarClockEverySecond() = afterAttachTry {
        if (ONE_UI_VERSION >= 90000) {
            val towerClass = classLoader.loadClass("com.android.systemui.statusbar.policy.QSClockBellTower")
            xposedModule.hook(towerClass.declaredConstructors.single()).intercept { chain ->
                val result = chain.proceed()
                try {
                    val tower = chain.thisObject
                    val handler = tower.reflect["mHandler"] as Handler
                    val updateTime = tower.reflect["mUpdateNotifyNewClockTime"] as Runnable
                    // Use the clock's own update task, independently of QuickStar's panel state.
                    handler.post(object : Runnable {
                        override fun run() {
                            updateTime.run()
                            handler.postAtTime(this, SystemClock.uptimeMillis() + 1000 - System.currentTimeMillis() % 1000)
                        }
                    })
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }

            val clockClass = classLoader.loadClass("com.android.systemui.statusbar.policy.QSClockIndicatorView")
            xposedModule.hook(clockClass.getDeclaredMethod("onAttachedToWindow")).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as TextView).fontFeatureSettings = "tnum"
                result
            }
        } else {
            // 每秒更新
            try {
                val helperClass = classLoader.loadClass(
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
                val controllerClass = classLoader.loadClass(
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
    }

    context(xposedModule: XposedModule)
    fun setStatusBarClockTextScale(scale: Float) = afterAttachTry {
        if (ONE_UI_VERSION >= 90000) {
            val controllerClass = classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController"
            )
            val pending = mutableMapOf<TextView, Runnable>()
            val method = controllerClass.getDeclaredMethod("onDensityOrFontScaleChanged")
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    val view = chain.thisObject.reflect["view"] as TextView
                    pending.remove(view)?.let { view.removeCallbacks(it) }
                    val task = Runnable {
                        pending.remove(view)
                        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * scale)
                    }
                    pending[view] = task
                    // One UI 9 posts its size update; scale afterwards, once per pending update.
                    view.post(task)
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } else {
            val controllerClass = classLoader.loadClass(
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
        }
    }

    context(xposedModule: XposedModule)
    fun setStatusBarClockFormat(format: String) {
        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
        setStatusBarClockText {
            dateTimeFormatter.format(LocalDateTime.now())
        }
    }

    context(xposedModule: XposedModule)
    private fun setStatusBarClockText(block: () -> String) = afterAttachTry {
        val clockClass = classLoader.loadClass(
            "com.android.systemui.statusbar.policy.QSClockIndicatorView"
        )
        val qsClockBellSoundClass = classLoader.loadClass(
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
    }

    context(xposedModule: XposedModule)
    fun hideSecureFolderStatusBarIcon() = afterAttachTry {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val controllerImplClass = classLoader.loadClass(
                "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl"
            )
            val holderClass = classLoader.loadClass(
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
            val controllerImplClass = classLoader.loadClass(
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
    }

    context(xposedModule: XposedModule)
    fun restoreBluetoothStatusBarIcon() = afterAttachTry {
        val controllerImplClass = classLoader.loadClass(
            "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl"
        )
        val iconManagerClass = classLoader.loadClass(
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
    }

    context(xposedModule: XposedModule)
    fun doubleTapStatusBarToSleep() {
        var lastTapTime = 0L

        fun lockScreen(context: Context) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            powerManager?.reflect?.call("goToSleep", SystemClock.uptimeMillis())
        }

        afterAttachTry {
            val viewClass = classLoader.loadClass(
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
        }
    }

    context(xposedModule: XposedModule)
    fun hideLockscreenStatusBar() = afterAttachTry {
        val viewClass = classLoader.loadClass(
            "com.android.systemui.statusbar.phone.KeyguardStatusBarView"
        )
        val method = viewClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
        xposedModule.hook(method).intercept { chain ->
            val newArgs = chain.args.toTypedArray()
            newArgs[0] = View.GONE
            chain.proceed(newArgs)
        }
    }

    context(xposedModule: XposedModule)
    fun setCustomCarrierName(carrierName: String) = afterAttachTry {
        val managerClass = classLoader.loadClass(
            "com.android.keyguard.CarrierTextManager"
        )
        val callbackInfoClass = classLoader.loadClass(
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
    }

    @SuppressLint("SetTextI18n")
    context(xposedModule: XposedModule)
    fun addBatteryLevelText(
        hidePercentSign: Boolean,
        hideChargingIcon: Boolean,
    ) = afterAttachTry(ONE_UI_VERSION >= 70000) {
        val batteryMeterViewClass = classLoader.loadClass("com.android.systemui.battery.BatteryMeterView")
        val viewId = View.generateViewId()

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
    }
}
