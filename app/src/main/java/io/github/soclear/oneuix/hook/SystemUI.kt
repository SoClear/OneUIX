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
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.ONE_UI_VERSION
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt


object SystemUI {
    enum class QsBar {
        MediaPlayer,
        NearbyDevicesAndDeviceControl,
        SecurityFooter,
        DataUsage,
        SmartViewAndModes,
    }

    fun setStatusBarPaddingDp(loadPackageParam: LoadPackageParam, left: Float?, right: Float?) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
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
        if (loadPackageParam.packageName != Package.SYSTEMUI || widthScale == null && heightScale == null) return
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

    fun supportOutdoorMode(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        fun outdoorModeRowTag() = "io.github.soclear.oneuix.outdoor_mode_row"

        fun isOutdoorModeEnabled(context: Context): Boolean {
            return (callStaticMethod(
                android.provider.Settings.System::class.java,
                "getIntForUser",
                context.contentResolver,
                "display_outdoor_mode",
                0,
                -2
            ) as Int) != 0
        }

        fun setOutdoorModeEnabled(context: Context, enabled: Boolean) {
            callStaticMethod(
                android.provider.Settings.System::class.java,
                "putIntForUser",
                context.contentResolver,
                "display_outdoor_mode",
                if (enabled) 1 else 0,
                -2
            )
        }

        @SuppressLint("DiscouragedApi")
        fun addOutdoorModeRow(
            context: Context,
            detailView: ViewGroup,
            switchPreferenceClass: Class<*>
        ) {
            try {
                val outdoorContainer = callStaticMethod(
                    switchPreferenceClass,
                    "inflateSwitch",
                    context,
                    detailView
                ) as View
                outdoorContainer.tag = outdoorModeRowTag()

                val res = context.resources
                val titleId = res.getIdentifier(
                    "sec_brightness_outdoor_mode_title",
                    "string",
                    Package.SYSTEMUI
                )
                val summaryId = res.getIdentifier(
                    "sec_brightness_outdoor_mode_summary",
                    "string",
                    Package.SYSTEMUI
                )
                val titleViewId = res.getIdentifier("title", "id", Package.SYSTEMUI)
                val summaryViewId = res.getIdentifier("title_summary", "id", Package.SYSTEMUI)
                val switchViewId = res.getIdentifier("title_switch", "id", Package.SYSTEMUI)
                if (titleId == 0 || titleViewId == 0 || switchViewId == 0) return

                outdoorContainer.findViewById<TextView>(titleViewId)?.text =
                    res.getString(titleId)

                outdoorContainer.findViewById<TextView>(summaryViewId)?.apply {
                    text = if (summaryId != 0) res.getString(summaryId) else ""
                    visibility = if (summaryId != 0) View.VISIBLE else View.GONE
                }

                val outdoorSwitch: CompoundButton? = outdoorContainer.findViewById(switchViewId)
                outdoorSwitch?.isChecked = isOutdoorModeEnabled(context)
                outdoorSwitch?.setOnCheckedChangeListener { _, isChecked ->
                    setOutdoorModeEnabled(context, isChecked)
                }
                outdoorContainer.setOnClickListener {
                    val switch = outdoorSwitch ?: return@setOnClickListener
                    switch.isChecked = !switch.isChecked
                }

                // Keep the row directly below Samsung's Adaptive brightness row.
                val index = minOf(2, detailView.childCount)
                detailView.addView(outdoorContainer, index)
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        try {
            val switchPreferenceClass = findClass(
                "com.android.systemui.qs.SecQSSwitchPreference",
                loadPackageParam.classLoader
            )
            (findClassIfExists(
                "com.android.systemui.settings.brightness.BrightnessDetailAdapter",
                loadPackageParam.classLoader
            ) ?: findClassIfExists(
                $$"com.android.systemui.settings.brightness.BrightnessDetail$1",
                loadPackageParam.classLoader
            ))?.let { brightnessDetailClass ->
                findAndHookMethod(
                    brightnessDetailClass,
                    "createDetailView",
                    Context::class.java,
                    View::class.java,
                    ViewGroup::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val detailView = param.result as? ViewGroup ?: return
                            if (detailView.findViewWithTag<View>(outdoorModeRowTag()) != null) return
                            val context = param.args[0] as Context
                            addOutdoorModeRow(context, detailView, switchPreferenceClass)
                        }
                    }
                )
            }
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

    fun hideSmartViewQsTile(loadPackageParam: LoadPackageParam) {
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
                        if (param.args[0] == "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)") {
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

        if (QsBar.NearbyDevicesAndDeviceControl in qsBarSet && ONE_UI_VERSION < 80500) {
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

        if (QsBar.MediaPlayer in qsBarSet && ONE_UI_VERSION < 80500) {
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
                if (ONE_UI_VERSION >= 80500) {
                    findClassIfExists(
                        $$"com.android.systemui.samsung.quicksetting.ui.banner.BottomBannerViewModel$1$1",
                        loadPackageParam.classLoader
                    )?.let { bottomBannerTransformClass ->
                        hookAllMethods(
                            bottomBannerTransformClass,
                            "invoke",
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (param.args.getOrNull(1) is Boolean) {
                                        param.args[1] = false
                                    }
                                }
                            }
                        )
                    }
                } else if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
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

        if (QsBar.DataUsage in qsBarSet) {
            try {
                if (ONE_UI_VERSION >= 80500) {
                    findClassIfExists(
                        $$"com.android.systemui.samsung.quicksetting.ui.banner.BottomBannerViewModel$1$1",
                        loadPackageParam.classLoader
                    )?.let { bottomBannerTransformClass ->
                        hookAllMethods(
                            bottomBannerTransformClass,
                            "invoke",
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (param.args.getOrNull(2) is Boolean) {
                                        param.args[2] = false
                                    }
                                }
                            }
                        )
                    }
                } else {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.DataUsageBar",
                        loadPackageParam.classLoader,
                        "isAvailable",
                        returnConstant(false)
                    )
                }
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        if (QsBar.SmartViewAndModes in qsBarSet && ONE_UI_VERSION < 80500) {
            try {
                findAndHookMethod(
                    "com.android.systemui.qs.bar.BarItemImpl",
                    loadPackageParam.classLoader,
                    "showBar",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val tag = getObjectField(param.thisObject, "TAG")
                            if (tag == "SmartViewLargeTileBar") {
                                param.args[0] = false
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        // 横屏
        try {
            if (ONE_UI_VERSION >= 80500) return
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
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
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
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
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


    fun setStatusBarMaxNotificationIcons(loadPackageParam: LoadPackageParam, max: Int) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            max < 0 ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return

        if (ONE_UI_VERSION >= 80500) {
            try {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.NotificationIconContainer",
                    loadPackageParam.classLoader,
                    "shouldForceOverflow",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[2] = max
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }

            try {
                hookAllConstructors(
                    findClass(
                        "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel",
                        loadPackageParam.classLoader
                    ),
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            setIntField(param.thisObject, "maxIcons", Int.MAX_VALUE)
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
            return
        }
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
                            ellipsize = null
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
                    var result = ""
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val shortDateText = getObjectField(param.args[0], "ShortDateText") as String
                        if (shortDateText != previousDate) {
                            previousDate = shortDateText
                            result = "$shortDateText\n${TraditionalChineseCalendar.getMonthAndDay()}"
                        }
                        val dateTextView = param.thisObject as TextView
                        if (dateTextView.text != result) {
                            dateTextView.text = result
                        }
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
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
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
                $$"com.android.systemui.qs.bar.VolumeToggleSeekBar$VolumeSeekbarChangeListener",
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
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
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

    fun disableNotificationGrouping(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "android.service.notification.StatusBarNotification",
                loadPackageParam.classLoader,
                "isGroup",
                returnConstant(false)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        // isGroup()=false lets children show individually, but the group summary
        // (FLAG_GROUP_SUMMARY) leaks through as a standalone entry whose dismissal
        // clears all the app's notifications. Filter it out of the shade list
        // while keeping it in NotifCollection so lifecycle events stay consistent.
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.notification.collection.ShadeListBuilder",
                loadPackageParam.classLoader,
                "applyFilters",
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                Long::class.javaPrimitiveType,
                List::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val entry = param.args[0] ?: return
                            val sbn = getObjectField(entry, "mSbn") ?: return
                            val notification = callMethod(sbn, "getNotification") ?: return
                            if (callMethod(notification, "isGroupSummary") as Boolean) {
                                param.result = true
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideOngoingActivityMedia(loadPackageParam: LoadPackageParam, packages: Set<String>) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || packages.isEmpty()) return
        try {
            findAndHookMethod(
                "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl",
                loadPackageParam.classLoader,
                "onNotificationAdded",
                String::class.java,
                "android.service.notification.StatusBarNotification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val sbn = param.args[1] ?: return
                            val packageName = callMethod(sbn, "getPackageName") as String
                            if (packageName in packages) {
                                param.result = null
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
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

    fun autoExpandNotifications(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
                loadPackageParam.classLoader,
                "isExpanded",
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val row = param.thisObject
                            // 确保非分组展开开关被打开
                            setBooleanField(row, "mEnableNonGroupedNotificationExpand", true)
                            // 1. 锁屏敏感隐私校验
                            val shouldShowPublic = callMethod(row, "shouldShowPublic") as Boolean
                            if (shouldShowPublic) {
                                // 锁屏隐藏敏感内容时不展开
                                return
                            }
                            // 2. 锁屏状态与 keyguard 约束校验
                            val onKeyguard = XposedHelpers.getBooleanField(row, "mOnKeyguard")
                            val allowOnKeyguard = param.args[0] as Boolean
                            if (onKeyguard && !allowOnKeyguard) {
                                return
                            }
                            // 3. 用户手动折叠校验（若用户手动折叠了该单条通知，则不强制展开）
                            val hasUserChanged =
                                XposedHelpers.getBooleanField(row, "mHasUserChangedExpansion")
                            if (!hasUserChanged) {
                                param.setResult(true)
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setDoubleLineStatusBar(loadPackageParam: LoadPackageParam, heightScale: Float) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || heightScale <= 1f) return

        // 1. 放大状态栏高度：一处 hook SystemBarUtils 覆盖所有消费者
        //    (IndicatorGardenPresenter / StatusBarWindowController 等都通过它取高度)
        try {
            val cls = findClass(
                "com.android.internal.policy.SystemBarUtils",
                loadPackageParam.classLoader
            )
            val cb = object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val h = p.result as Int
                    if (h > 0) p.result = (h * heightScale).toInt()
                }
            }
            hookAllMethods(cls, "getStatusBarHeight", cb)
            hookAllMethods(cls, "getStatusBarHeightForRotation", cb)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        // 2. 双行布局：
        //    IndicatorGarden 在 updateGarden() 里直接设置 PhoneStatusBarView 和
        //    status_bar_contents 的 layoutParams(height/topMargin)。由于 totalHeight
        //    被放大了 heightScale 倍，contents 被撑满双倍高度，子 View(left/center/right)
        //    在其中垂直居中/底部对齐，两行重叠到底部。
        //
        //    解决：在 updateGarden 返回后，同步把 contents 的高度收缩回单行
        //    （保持原有 topMargin 以兼容刘海），避免异步竞争和布局循环。
        //    通过阈值检测（高度是否被放大）防止重复收缩。
        try {
            val gardenerCls = findClass(
                "com.android.systemui.statusbar.phone.IndicatorBasicGardener",
                loadPackageParam.classLoader
            )
            val modelCls = findClass(
                "com.android.systemui.statusbar.phone.IndicatorGardenModel",
                loadPackageParam.classLoader
            )
            val inputPropsCls = findClass(
                "com.android.systemui.statusbar.phone.IndicatorGardenInputProperties",
                loadPackageParam.classLoader
            )
            findAndHookMethod(gardenerCls, "updateGarden", modelCls, inputPropsCls,
                object : XC_MethodHook() {
                    private var cachedSingleLineHeight = -1
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val gardenView = XposedHelpers.getObjectField(p.thisObject, "gardenView")
                            val hc = XposedHelpers.callMethod(gardenView, "getHeightContainer") as? ViewGroup
                                ?: return
                            if (hc.javaClass.name != "com.android.systemui.statusbar.phone.PhoneStatusBarView") return
                            val sbv = hc as FrameLayout
                            val res = sbv.resources
                            val pkg = Package.SYSTEMUI

                            if (cachedSingleLineHeight <= 0) {
                                val sbhId = res.getIdentifier("status_bar_height", "dimen", "android")
                                cachedSingleLineHeight = if (sbhId != 0) res.getDimensionPixelSize(sbhId) else 0
                            }

                            val contentsId = res.getIdentifier("status_bar_contents", "id", pkg)
                            val contents = sbv.findViewById<View>(contentsId) ?: return
                            val cLp = contents.layoutParams as? FrameLayout.LayoutParams
                                ?: return
                            val currentTotal = cLp.topMargin + cLp.height + cLp.bottomMargin
                            val sh = cachedSingleLineHeight
                            if (sh > 0 && currentTotal > sh * 1.5f) {
                                val targetHeight = sh - cLp.topMargin - cLp.bottomMargin
                                if (targetHeight > 0 && cLp.height != targetHeight) {
                                    cLp.height = targetHeight
                                    if (cLp.gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.TOP) {
                                        cLp.gravity = (cLp.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or Gravity.TOP
                                    }
                                    contents.layoutParams = cLp
                                }
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        // 3. 创建第二行水平容器，把 ongoing 实时通知（通话/媒体）和通知图标
        //    从顶部行移到底部居左排列：[通话chip] [媒体胶囊(歌词)] [通知图标...]
        //    媒体胶囊设置 weight=1 以占据更多水平空间，支持显示更长歌词。
        //    使用持续的 OnGlobalLayoutListener 防止系统代码把视图移回原位或
        //    把 LayoutParams 重置为 FrameLayout.LayoutParams 导致崩溃。
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                loadPackageParam.classLoader,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val sbv = p.thisObject as FrameLayout
                        val key = "oneuix_dl_bottom_row"
                        if (sbv.getTag(key.hashCode()) != null) return
                        sbv.setTag(key.hashCode(), true)

                        val chipIds = listOf("ongoing_call_chip", "ongoing_activity_capsule")
                        val notifAreaIdName = "notification_icon_area"
                        val bottomRowKey = "oneuix_dl_bottom_row_container"

                        sbv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                            private var currentContentsPadLeft = -1
                            override fun onGlobalLayout() {
                                try {
                                    val res = sbv.resources
                                    val pkg = Package.SYSTEMUI
                                    val contentsId = res.getIdentifier("status_bar_contents", "id", pkg)
                                    val contents = sbv.findViewById<View>(contentsId) ?: return
                                    val startPad = contents.paddingLeft

                                    var bottomRow = sbv.getTag(bottomRowKey.hashCode()) as? LinearLayout
                                    if (bottomRow == null) {
                                        bottomRow = LinearLayout(sbv.context).apply {
                                            orientation = LinearLayout.HORIZONTAL
                                            gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                                        }
                                        val flp = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            Gravity.BOTTOM or Gravity.START
                                        )
                                        flp.marginStart = startPad
                                        flp.marginEnd = startPad
                                        sbv.addView(bottomRow, flp)
                                        sbv.setTag(bottomRowKey.hashCode(), bottomRow)
                                    } else if (currentContentsPadLeft != startPad) {
                                        val brLp = bottomRow.layoutParams as? FrameLayout.LayoutParams
                                        if (brLp != null) {
                                            brLp.marginStart = startPad
                                            brLp.marginEnd = startPad
                                            bottomRow.layoutParams = brLp
                                        }
                                        currentContentsPadLeft = startPad
                                    }

                                    val gap = TypedValue.applyDimension(
                                        TypedValue.COMPLEX_UNIT_DIP, 4f, res.displayMetrics
                                    ).toInt()

                                    for (idName in chipIds) {
                                        val id = res.getIdentifier(idName, "id", pkg)
                                        val v = sbv.findViewById<View>(id) ?: continue
                                        val parent = v.parent
                                        if (parent !== bottomRow) {
                                            (parent as? ViewGroup)?.removeView(v)
                                        }
                                        if (v.parent !== bottomRow) {
                                            val llp = LinearLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                            val idx = bottomRow.indexOfChild(v)
                                            val insertAt = if (idName == "ongoing_call_chip") 0 else bottomRow.childCount
                                            if (insertAt > 0) llp.marginStart = gap
                                            if (idName == "ongoing_activity_capsule") {
                                                llp.weight = 1f
                                                llp.width = 0
                                            }
                                            if (idx >= 0) {
                                                bottomRow.updateViewLayout(v, llp)
                                            } else {
                                                bottomRow.addView(v, insertAt.coerceAtMost(bottomRow.childCount), llp)
                                            }
                                        } else {
                                            val llp = v.layoutParams as? LinearLayout.LayoutParams
                                            if (llp != null) {
                                                var changed = false
                                                if (idName == "ongoing_activity_capsule") {
                                                    if (llp.width != 0 || llp.weight != 1f) {
                                                        llp.width = 0
                                                        llp.weight = 1f
                                                        changed = true
                                                    }
                                                } else {
                                                    if (llp.width != ViewGroup.LayoutParams.WRAP_CONTENT || llp.weight != 0f) {
                                                        llp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                                        llp.weight = 0f
                                                        changed = true
                                                    }
                                                }
                                                if (changed) v.layoutParams = llp
                                            } else {
                                                val newLp = LinearLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                                )
                                                if (idName == "ongoing_activity_capsule") {
                                                    newLp.weight = 1f
                                                    newLp.width = 0
                                                }
                                                val insertAt = if (idName == "ongoing_call_chip") 0 else -1
                                                val idx = bottomRow.indexOfChild(v)
                                                if (idx >= 0) bottomRow.updateViewLayout(v, newLp)
                                            }
                                        }
                                    }

                                    val areaId = res.getIdentifier(notifAreaIdName, "id", pkg)
                                    val notifArea = sbv.findViewById<View>(areaId) ?: return
                                    val nParent = notifArea.parent
                                    if (nParent !== bottomRow) {
                                        (nParent as? ViewGroup)?.removeView(notifArea)
                                    }
                                    if (notifArea.parent !== bottomRow) {
                                        val llp = LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        if (bottomRow.childCount > 0) llp.marginStart = gap
                                        bottomRow.addView(notifArea, llp)
                                    } else {
                                        val llp = notifArea.layoutParams as? LinearLayout.LayoutParams
                                        if (llp != null && (llp.width != ViewGroup.LayoutParams.WRAP_CONTENT || llp.weight != 0f)) {
                                            llp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                                            llp.weight = 0f
                                            notifArea.layoutParams = llp
                                        }
                                    }

                                    try {
                                        val capsuleId = res.getIdentifier("ongoing_activity_capsule", "id", pkg)
                                        val capsule = sbv.findViewById<View>(capsuleId)
                                        val rvId = res.getIdentifier("capsule_recyclerview", "id", pkg)
                                        if (capsule != null && rvId != 0) {
                                            val rv = capsule.findViewById<View>(rvId)
                                            if (rv != null) {
                                                val lp = rv.layoutParams
                                                if (lp != null && lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                                                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                                    rv.layoutParams = lp
                                                }
                                                val screenW = res.displayMetrics.widthPixels
                                                fun unlock(v: View) {
                                                    when (v) {
                                                        is TextView -> {
                                                            v.maxWidth = screenW
                                                            v.setHorizontallyScrolling(true)
                                                            v.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                                                        }
                                                        is ViewGroup -> for (j in 0 until v.childCount) unlock(v.getChildAt(j))
                                                    }
                                                }
                                                fun forceAdapterFields() {
                                                    val rvCls = rv.javaClass
                                                    val getAdapter = rvCls.methods.firstOrNull { it.name == "getAdapter" && it.parameterCount == 0 }
                                                    val adapter = getAdapter?.invoke(rv) ?: return
                                                    val zeroF = listOf(
                                                        "availableSpace", "marqueeLimitedWidth", "shadowWidth",
                                                        "notificationIconWidth", "infoTextTotalMargin", "cachedVisibleIconCount"
                                                    )
                                                    val largeF = listOf(
                                                        "enableMaxWidth", "maximumWidth", "maxChipWidth", "sportScoreMaxWidth"
                                                    )
                                                    for (fn in zeroF) {
                                                        try {
                                                            val f = adapter.javaClass.getDeclaredField(fn)
                                                            f.isAccessible = true
                                                            f.setInt(adapter, 0)
                                                        } catch (_: Throwable) {}
                                                    }
                                                    for (fn in largeF) {
                                                        try {
                                                            val f = adapter.javaClass.getDeclaredField(fn)
                                                            f.isAccessible = true
                                                            f.setInt(adapter, screenW)
                                                        } catch (_: Throwable) {}
                                                    }
                                                    val getChildCount = rvCls.methods.firstOrNull { it.name == "getChildCount" && it.parameterCount == 0 }
                                                    val getChildAt = rvCls.methods.firstOrNull { it.name == "getChildAt" && it.parameterCount == 1 }
                                                    val getChildViewHolder = rvCls.methods.firstOrNull { it.name == "getChildViewHolder" && it.parameterCount == 1 }
                                                    val childCount = getChildCount?.invoke(rv) as? Int ?: 0
                                                    for (i in 0 until childCount) {
                                                        val child = getChildAt?.invoke(rv, i) as? View ?: continue
                                                        val holder = getChildViewHolder?.invoke(rv, child) ?: continue
                                                        try {
                                                            val f = holder.javaClass.getDeclaredField("maximumWidth")
                                                            f.isAccessible = true
                                                            f.setInt(holder, screenW)
                                                        } catch (_: Throwable) {}
                                                        try {
                                                            val f = holder.javaClass.getDeclaredField("infoTextExtra")
                                                            f.isAccessible = true
                                                            f.setInt(holder, 0)
                                                        } catch (_: Throwable) {}
                                                        val itemView = try {
                                                            holder.javaClass.getField("itemView").apply { isAccessible = true }.get(holder) as? View
                                                        } catch (_: Throwable) { null }
                                                        if (itemView != null) unlock(itemView)
                                                    }
                                                }
                                                rv.post {
                                                    try { forceAdapterFields() } catch (_: Throwable) {}
                                                }
                                                forceAdapterFields()
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                } catch (t: Throwable) {
                                    XposedBridge.log(t)
                                }
                            }
                        })
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        // 4. 解除 OngoingChipAdapter 对媒体胶囊宽度的限制，支持显示长歌词。
        //    核心思路：直接 hook 宽度判定和绑定方法（而不是仅靠 OnGlobalLayout 反射字段，
        //    因为系统有自己的 OnLayoutChangeListener/协程在持续覆写宽度值）：
        //    a) 让 hasAvailableSpaceToExpand() 永远返回 true，系统就永远认为空间充足、
        //       会展开显示文本而不是收缩成只有图标的小 chip。
        //    b) 在 onBindViewHolder() 之后，递归解除 ViewHolder 里所有 TextView 的 maxWidth
        //       限制，并修正 ChipViewHolder.maximumWidth。
        //    c) hook 内部的 containerOnLayoutChangeListener.onLayoutChange()，
        //       在系统设置完 enableMaxWidth 之后再强制覆盖为屏幕宽度。
        //    d) 在构造函数里放大几个宽度相关字段作为第二道保险。
        try {
            val adapterCls = findClass(
                "com.android.systemui.statusbar.phone.ongoingactivity.OngoingChipAdapter",
                loadPackageParam.classLoader
            )

            // a) hasAvailableSpaceToExpand 永远返回 true
            findAndHookMethod(adapterCls, "hasAvailableSpaceToExpand", Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = true
                    }
                }
            )

            // b) onBindViewHolder 之前和之后都做处理：
            //    - beforeHookedMethod: 提前把所有宽度字段设为正确值（开销/阈值=0，上限=屏宽），
            //      这样系统计算 i5 = enableMaxWidth - i4 时得到足够大的值，不会进入收缩分支。
            //    - afterHookedMethod: 解除 TextView maxWidth 限制、修正容器宽度、Viewholder 字段。
            val vhClsName = "com.android.systemui.statusbar.phone.ongoingactivity.OngoingChipAdapter\$ChipViewHolder"
            val vhCls = try { findClass(vhClsName, loadPackageParam.classLoader) } catch (_: Throwable) { null }
            val zf = listOf("availableSpace", "marqueeLimitedWidth", "shadowWidth",
                "notificationIconWidth", "infoTextTotalMargin", "cachedVisibleIconCount")
            val lf = listOf("enableMaxWidth", "maximumWidth", "maxChipWidth", "sportScoreMaxWidth")
            hookAllMethods(adapterCls, "onBindViewHolder", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    try {
                        val ctx = adapterCls.getDeclaredField("mContext").let {
                            it.isAccessible = true
                            it.get(p.thisObject) as? Context
                        } ?: return
                        val screenW = ctx.resources.displayMetrics.widthPixels
                        for (fn in zf) {
                            try { val f = adapterCls.getDeclaredField(fn); f.isAccessible = true; f.setInt(p.thisObject, 0) } catch (_: Throwable) {}
                        }
                        for (fn in lf) {
                            try { val f = adapterCls.getDeclaredField(fn); f.isAccessible = true; f.setInt(p.thisObject, screenW) } catch (_: Throwable) {}
                        }
                        val holder = p.args[0] ?: return
                        if (vhCls != null && vhCls.isInstance(holder)) {
                            try { val f = vhCls.getDeclaredField("maximumWidth"); f.isAccessible = true; f.setInt(holder, screenW) } catch (_: Throwable) {}
                            try { val f = vhCls.getDeclaredField("infoTextExtra"); f.isAccessible = true; f.setInt(holder, 0) } catch (_: Throwable) {}
                        }
                    } catch (_: Throwable) {}
                }
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val holder = p.args[0] ?: return
                        val mContextField = runCatching {
                            val f = adapterCls.getDeclaredField("mContext")
                            f.isAccessible = true
                            f.get(p.thisObject) as Context
                        }.getOrNull() ?: return
                        val largeW = mContextField.resources.displayMetrics.widthPixels
                        val itemView = try {
                            holder.javaClass.getField("itemView").apply { isAccessible = true }.get(holder) as? View
                        } catch (_: Throwable) { null } ?: return
                        fun unlock(v: View) {
                            when (v) {
                                is TextView -> {
                                    v.maxWidth = largeW
                                    v.setHorizontallyScrolling(true)
                                    v.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                                }
                                is ViewGroup -> {
                                    for (j in 0 until v.childCount) unlock(v.getChildAt(j))
                                }
                            }
                        }
                        fun forceContainerWidth(v: View) {
                            val lp = v.layoutParams
                            if (lp != null && lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                v.layoutParams = lp
                            }
                        }
                        val fixViews = listOf("mNotiParentLayout", "mRemoteContainer", "mExpandedInfo")
                        for (vn in fixViews) {
                            try {
                                val f = holder.javaClass.getDeclaredField(vn)
                                f.isAccessible = true
                                val v = f.get(holder) as? View ?: continue
                                forceContainerWidth(v)
                            } catch (_: Throwable) {}
                        }
                        forceContainerWidth(itemView)
                        itemView.post {
                            try {
                                unlock(itemView)
                                for (vn in fixViews) {
                                    try {
                                        val f = holder.javaClass.getDeclaredField(vn)
                                        f.isAccessible = true
                                        val v = f.get(holder) as? View ?: continue
                                        forceContainerWidth(v)
                                    } catch (_: Throwable) {}
                                }
                            } catch (_: Throwable) {}
                        }
                        unlock(itemView)
                        try {
                            if (vhCls != null && vhCls.isInstance(holder)) {
                                val f1 = vhCls.getDeclaredField("maximumWidth")
                                f1.isAccessible = true
                                f1.setInt(holder, largeW)
                                val f2 = vhCls.getDeclaredField("infoTextExtra")
                                f2.isAccessible = true
                                f2.setInt(holder, 0)
                            }
                        } catch (_: Throwable) {}
                    } catch (_: Throwable) {}
                }
            })

            // c) 构造函数里设置宽度字段：
            //    - "预留开销/阈值"类字段（会从总宽度中减去，或作为收缩阈值）→ 设为 0
            //    - "上限"类字段（maxWidth、maximumWidth 等）→ 设为屏幕宽度
            //    这样 i5 = enableMaxWidth - (availableSpace+shadowWidth+notifIcon) = screenW - 0 = screenW,
            //    肯定大于 marqueeLimitedWidth(=0)，系统就不会收缩成仅显示图标的 chip 模式。
            val zeroFields = listOf(
                "availableSpace", "marqueeLimitedWidth", "shadowWidth",
                "notificationIconWidth", "infoTextTotalMargin", "cachedVisibleIconCount"
            )
            val largeFields = listOf(
                "maximumWidth", "maxChipWidth", "sportScoreMaxWidth"
            )
            hookAllConstructors(adapterCls, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val ctx = adapterCls.getDeclaredField("mContext").let {
                            it.isAccessible = true
                            it.get(p.thisObject) as Context
                        }
                        val screenW = ctx.resources.displayMetrics.widthPixels
                        for (fName in zeroFields) {
                            try {
                                val f = adapterCls.getDeclaredField(fName)
                                f.isAccessible = true
                                f.setInt(p.thisObject, 0)
                            } catch (_: Throwable) {}
                        }
                        for (fName in largeFields) {
                            try {
                                val f = adapterCls.getDeclaredField(fName)
                                f.isAccessible = true
                                f.setInt(p.thisObject, screenW)
                            } catch (_: Throwable) {}
                        }
                        try {
                            val f = adapterCls.getDeclaredField("enableMaxWidth")
                            f.isAccessible = true
                            f.setInt(p.thisObject, screenW)
                        } catch (_: Throwable) {}
                    } catch (_: Throwable) {}
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        // d) Hook containerOnLayoutChangeListener.onLayoutChange 在系统设置完
        //    enableMaxWidth 后覆盖正确的值，避免点击后又被缩短。
        //    注意：预留开销类字段置 0，上限类字段置屏幕宽度。
        try {
            val listenerCls = findClass(
                "com.android.systemui.statusbar.phone.ongoingactivity.OngoingActivityController\$containerOnLayoutChangeListener\$1",
                loadPackageParam.classLoader
            )
            findAndHookMethod(listenerCls, "onLayoutChange",
                View::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val this0 = p.thisObject
                            val outer = this0.javaClass.declaredFields.firstOrNull {
                                it.type.name == "com.android.systemui.statusbar.phone.ongoingactivity.OngoingActivityController"
                            }?.apply { isAccessible = true }?.get(this0) ?: return
                            val adapterField = outer.javaClass.getDeclaredField("mOngoingChipAdapter")
                            adapterField.isAccessible = true
                            val adapter = adapterField.get(outer) ?: return
                            val ctxField = outer.javaClass.getDeclaredField("mContext")
                            ctxField.isAccessible = true
                            val ctx = ctxField.get(outer) as Context
                            val screenW = ctx.resources.displayMetrics.widthPixels
                            val zeroF = listOf(
                                "availableSpace", "marqueeLimitedWidth", "shadowWidth",
                                "notificationIconWidth", "infoTextTotalMargin", "cachedVisibleIconCount"
                            )
                            val largeF = listOf(
                                "enableMaxWidth", "maximumWidth", "maxChipWidth", "sportScoreMaxWidth"
                            )
                            for (fn in zeroF) {
                                try {
                                    val f = adapter.javaClass.getDeclaredField(fn)
                                    f.isAccessible = true
                                    f.setInt(adapter, 0)
                                } catch (_: Throwable) {}
                            }
                            for (fn in largeF) {
                                try {
                                    val f = adapter.javaClass.getDeclaredField(fn)
                                    f.isAccessible = true
                                    f.setInt(adapter, screenW)
                                } catch (_: Throwable) {}
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
