package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt


object SystemUI {
    enum class QsBar {
        MediaPlayer,
        NearbyDevicesAndDeviceControl,
        SecurityFooter,
        SmartViewAndModes,
    }

    fun setStatusBarPaddingDp(loadPackageParam: LoadPackageParam, left: Float?, right: Float?) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) {
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

    fun hideBatteryPercentageSign(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != Package.SYSTEMUI ||
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

    fun disableScreenshotCaptureSound(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            val screenshotCaptureSoundClass = findClass(
                "com.android.systemui.screenshot.${
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "sep."
                    else ""
                }ScreenshotCaptureSound", loadPackageParam.classLoader
            )
            hookAllMethods(screenshotCaptureSoundClass, "play", returnConstant(null))
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


    fun hideDeviceControlQsTile(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) return
        try {
            findAndHookMethod(
                "com.android.systemui.qs.QSTileHost",
                loadPackageParam.classLoader,
                "createTile",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "DeviceControl") {
                            param.result = null
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


    // related classes: BarFactory BarController  BarOrderInteractor
    fun hideQsBar(loadPackageParam: LoadPackageParam, qsBarSet: Set<QsBar>) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            qsBarSet.isEmpty() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = getObjectField(param.thisObject, "mBarRootView") as View?
                view?.visibility = View.GONE
            }
        }

        if (QsBar.NearbyDevicesAndDeviceControl in qsBarSet) {
            try {
                if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.BottomLargeTileBar",
                        loadPackageParam.classLoader,
                        "showBar",
                        Boolean::class.javaPrimitiveType,
                        callback
                    )
                } else {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.LargeTileBar",
                        loadPackageParam.classLoader,
                        "updateLayout",
                        LinearLayout::class.java,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val string = getObjectField(param.thisObject, "TAG") as String
                                if (string == "BottomLargeTileBar") {
                                    val view =
                                        getObjectField(param.thisObject, "mBarRootView") as View?
                                    view?.visibility = View.GONE
                                }
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        if (QsBar.MediaPlayer in qsBarSet) {
            try {
                findAndHookMethod(
                    "com.android.systemui.qs.bar.QSMediaPlayerBar",
                    loadPackageParam.classLoader,
                    "inflateViews",
                    ViewGroup::class.java,
                    callback
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        if (QsBar.SecurityFooter in qsBarSet) {
            try {
                if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.BarItemImpl",
                        loadPackageParam.classLoader,
                        "showBar",
                        Boolean::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val tag = getObjectField(param.thisObject, "TAG")
                                if (tag == "SecurityFooterBar") {
                                    param.args[0] = false
                                }
                            }
                        }
                    )
                    /* 另一种实现方式
                    findAndHookMethod(
                        "com.android.systemui.qs.QSSecurityFooter$3",
                        loadPackageParam.classLoader,
                        "run",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val qsSecurityFooter =
                                    XposedHelpers.getSurroundingThis(param.thisObject)
                                val securityFooterBar =
                                    getObjectField(qsSecurityFooter, "mVisibilityChangedListener")
                                val view =
                                    getObjectField(securityFooterBar, "mBarRootView") as View?
                                view?.visibility = View.GONE
                            }
                        }
                    )
                    */
                } else {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.SecurityFooterBar",
                        loadPackageParam.classLoader,
                        "onVisibilityChanged",
                        Int::class.javaPrimitiveType,
                        callback
                    )
                }
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        if (QsBar.SmartViewAndModes in qsBarSet) {
            try {
                findAndHookMethod(
                    "com.android.systemui.qs.bar.SmartViewLargeTileBar",
                    loadPackageParam.classLoader,
                    "showBar",
                    Boolean::class.javaPrimitiveType,
                    callback
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        // 横屏
        try {
            val nearbyDevicesAndDeviceControl = QsBar.NearbyDevicesAndDeviceControl in qsBarSet
            val smartViewAndModes = QsBar.SmartViewAndModes in qsBarSet
            if (!nearbyDevicesAndDeviceControl && !smartViewAndModes) {
                return
            }
            findAndHookMethod(
                "com.android.systemui.qs.bar.TopLargeTileBar",
                loadPackageParam.classLoader,
                "addTile",
                $$"com.android.systemui.qs.SecQSPanelControllerBase$TileRecord",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val tile = getObjectField(param.args[0], "tile")
                        val tileSpec = callMethod(tile, "getTileSpec")
                        val flag = when (tileSpec) {
                            "DeviceControl" if nearbyDevicesAndDeviceControl -> true
                            "custom(com.samsung.android.mydevice/.quicksettings.MyDeviceTileService)" if nearbyDevicesAndDeviceControl -> true
                            "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)" if smartViewAndModes -> true
                            "custom(com.samsung.android.app.routines/.LifestyleModeTile)" if smartViewAndModes -> true
                            else -> false
                        }
                        if (flag) {
                            param.result = null
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


    fun alwaysExpandQsTileChunk(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            findAndHookMethod(
                "com.android.systemui.qs.bar.TileChunkLayoutBar",
                loadPackageParam.classLoader,
                "setContainerHeight",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = getObjectField(param.thisObject, "mContainerExpandedHeight")
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            findAndHookMethod(
                "com.android.systemui.qs.bar.TileChunkLayoutBar",
                loadPackageParam.classLoader,
                "inflateViews",
                ViewGroup::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val scrollIndicator = getObjectField(
                            param.thisObject,
                            "mScrollIndicatorClickContainer"
                        ) as View
                        scrollIndicator.visibility = View.GONE
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


    fun alwaysShowTimeDateOnQs(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            // 单独
            findAndHookMethod(
                "com.android.systemui.qs.animator.PanelTransitionAnimator",
                loadPackageParam.classLoader,
                "setQs",
                "com.android.systemui.plugins.qs.QS",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                            setObjectField(param.thisObject, "clockDateContainer", null)
                            return
                        }
                        val context = getObjectField(param.thisObject, "context") as Context
                        setObjectField(param.thisObject, "clockDateContainer", View(context))
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            // 两者
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mContext = getObjectField(param.thisObject, "mContext") as Context
                    setObjectField(param.thisObject, "mClockDateContainer", View(mContext))
                }
            }
            if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                findAndHookMethod(
                    "com.android.systemui.qs.animator.LegacyQsExpandAnimator",
                    loadPackageParam.classLoader,
                    "updateViews$2",
                    callback
                )
            } else {
                findAndHookMethod(
                    "com.android.systemui.qs.animator.QsExpandAnimator",
                    loadPackageParam.classLoader,
                    "updateViews",
                    callback
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setQsClockStyle(
        loadPackageParam: LoadPackageParam,
        monospaced: Boolean,
        modifyTextSize: Boolean,
        textSize: Float
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || !monospaced && !modifyTextSize) {
            return
        }
        // 布局见 res/layout/sec_qqs_date_buttons.xml
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val clockView = getObjectField(param.thisObject, "mClockView") as TextView
                // 启用 tabular (等宽) 数字: 'tnum' 1
                // 禁用 proportional (不等宽) 数字: 'pnum' 0
                if (monospaced) {
                    clockView.fontFeatureSettings = "'tnum' 1, 'pnum' 0"
                }
                if (modifyTextSize) {
                    clockView.textSize = textSize

                    val density = clockView.context.resources.displayMetrics.density
                    // 15sp 到 70sp
                    val ratio = 0.00218181f * textSize * textSize + 0.16727272f * textSize
                    val padding = -(density * ratio).roundToInt()
                    clockView.apply {
                        setPadding(paddingLeft, padding, paddingRight, padding)
                    }
                }
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.qs.SecQuickStatusBarHeader",
                loadPackageParam.classLoader,
                "onFinishInflate",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
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

    fun setStatusBarClockText(loadPackageParam: LoadPackageParam, block: () -> String) {
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

    fun setStatusBarMaxNotificationIcons(loadPackageParam: LoadPackageParam, max: Int) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            max < 0 ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.NotificationIconContainer",
                loadPackageParam.classLoader,
                "shouldForceOverflow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[3] = max
                    }
                }
            )

            findAndHookMethod(
                "com.android.systemui.statusbar.phone.NotificationIconContainer",
                loadPackageParam.classLoader,
                "initResources",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        setIntField(param.thisObject, "mMaxStaticIcons", Int.MAX_VALUE)
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
                if (interval >= 40_000_000L && interval <= 300_000_000L) {
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


    fun showTraditionalChineseDateOnQS(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            val qsShortenDateClass = findClass(
                "com.android.systemui.statusbar.policy.QSShortenDate",
                loadPackageParam.classLoader
            )
            findAndHookConstructor(
                qsShortenDateClass,
                Context::class.java,
                AttributeSet::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val textView = param.thisObject as TextView
                        textView.apply {
                            isSingleLine = false
                            setLines(2)
                            setPadding(paddingLeft, -10, paddingRight, -10)
                            setLineSpacing(0f, 0.8f)
                            val density = context.resources.displayMetrics.density
                            translationY = -10 * density
                        }
                    }
                }
            )
            findAndHookMethod(
                qsShortenDateClass,
                "notifyTimeChanged",
                "com.android.systemui.statusbar.policy.QSClockBellSound",
                object : XC_MethodReplacement() {
                    var previousDate = ""

                    @SuppressLint("SetTextI18n")
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val shortDateText = getObjectField(param.args[0], "ShortDateText") as String
                        if (shortDateText == previousDate) return null
                        previousDate = shortDateText
                        val traditionalChineseDate = TraditionalChineseCalendar.getMonthAndDay()
                        val dateTextView = param.thisObject as TextView
                        dateTextView.text = "$shortDateText\n$traditionalChineseDate"
                        return null
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun addVolumeProgressToQsBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        var textView: TextView? = null

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val slider = getObjectField(param.thisObject, "mSlider") as View
                    val sliderParent = slider.parent as FrameLayout
                    textView = TextView(sliderParent.context).apply {
                        setTextColor(Color.WHITE)
                        val volumeSeekBar = getObjectField(param.thisObject, "mVolumeSeekBar")
                        val progress = getIntField(volumeSeekBar, "progress")
                        text = progress.toString()
                    }
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        marginEnd = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8.0f,
                            sliderParent.context.resources.displayMetrics
                        ).roundToInt()
                    }
                    sliderParent.addView(textView, layoutParams)
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }

        try {
            findAndHookMethod(
                "com.android.systemui.qs.bar.VolumeBar",
                loadPackageParam.classLoader,
                "inflateViews",
                ViewGroup::class.java,
                callback
            )
            findAndHookMethod(
                "com.android.systemui.qs.bar.VolumeToggleSeekBar\$VolumeSeekbarChangeListener",
                loadPackageParam.classLoader,
                "onProgressChanged",
                SeekBar::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        textView?.text = param.args[1].toString()
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


    fun addBrightnessProgressToQsBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        val textViewList = mutableListOf<TextView>()

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val view = getObjectField(param.thisObject, "mView")
                    val slider = getObjectField(view, "mSlider") as View
                    val frameLayout = slider.parent as FrameLayout

                    val textView = TextView(frameLayout.context).apply {
                        setTextColor(Color.WHITE)
                    }
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        marginEnd = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8.0f,
                            frameLayout.context.resources.displayMetrics
                        ).roundToInt()
                    }
                    textViewList.add(textView)
                    frameLayout.addView(textView, layoutParams)
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }

        try {
            findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSliderController",
                loadPackageParam.classLoader,
                "onViewAttached",
                callback
            )

            findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSliderController$2",
                loadPackageParam.classLoader,
                "onProgressChanged",
                SeekBar::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val progress = param.args[1].toString()
                        textViewList.forEach {
                            it.text = progress
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideAODStatusBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val batteryView = getObjectField(param.thisObject, "mView") as View
                    if (batteryView.tag == "PluginFaceWidgetManager") {
                        val parentView = batteryView.parent.parent as View
                        parentView.visibility = View.GONE
                    }
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.battery.BatteryMeterViewController",
                loadPackageParam.classLoader,
                "onViewAttached",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun aodLockSupportLunar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0] == "CscFeature_Calendar_EnableLocalHolidayDisplay") {
                    param.result = "CHINA"
                }
            }
        }

        try {
            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                String::class.java,
                callback
            )

            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
