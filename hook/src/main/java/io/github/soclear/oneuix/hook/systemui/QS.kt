package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import io.github.soclear.oneuix.hook.util.callMethod
import io.github.soclear.oneuix.hook.util.callStaticMethod
import io.github.soclear.oneuix.hook.util.get
import io.github.soclear.oneuix.hook.util.set
import io.github.soclear.oneuix.hook.util.xlog
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
object QS {
    enum class QsBar {
        MediaPlayer,
        NearbyDevicesAndDeviceControl,
        SecurityFooter,
        DataUsage,
        SmartViewAndModes,
    }


    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideDeviceControlQsTile() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) return
        try {
            val qsTileHostClass = param.classLoader.loadClass("com.android.systemui.qs.QSTileHost")
            val createTileMethod = qsTileHostClass.getDeclaredMethod("createTile", String::class.java)
            xposedModule.hook(createTileMethod).intercept { chain ->
                if (chain.args.firstOrNull() == "DeviceControl") {
                    null
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideSmartViewQsTile() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) return
        try {
            val qsTileHostClass = param.classLoader.loadClass("com.android.systemui.qs.QSTileHost")
            val createTileMethod = qsTileHostClass.getDeclaredMethod("createTile", String::class.java)
            xposedModule.hook(createTileMethod).intercept { chain ->
                if (chain.args.firstOrNull() == "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)") {
                    null
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    // related classes: BarFactory BarController  BarOrderInteractor
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideQsBar(qsBarSet: Set<QsBar>) {
        if (param.packageName != Package.SYSTEMUI ||
            qsBarSet.isEmpty() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }

        val hideBarCallback = XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            val view = chain.thisObject["mBarRootView"] as? View
            view?.visibility = View.GONE
            result
        }

        if (QsBar.NearbyDevicesAndDeviceControl in qsBarSet && ONE_UI_VERSION < 80500) {
            try {
                if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    val bottomLargeTileBarClass = param.classLoader.loadClass(
                        "com.android.systemui.qs.bar.BottomLargeTileBar"
                    )
                    val showBarMethod = bottomLargeTileBarClass.getDeclaredMethod(
                        "showBar",
                        Boolean::class.javaPrimitiveType
                    )
                    xposedModule.hook(showBarMethod).intercept(hideBarCallback)
                } else {
                    val largeTileBarClass = param.classLoader.loadClass(
                        "com.android.systemui.qs.bar.LargeTileBar"
                    )
                    val updateLayoutMethod = largeTileBarClass.getDeclaredMethod(
                        "updateLayout",
                        LinearLayout::class.java
                    )
                    xposedModule.hook(updateLayoutMethod).intercept { chain ->
                        val result = chain.proceed()
                        val string = chain.thisObject["TAG"] as? String
                        if (string == "BottomLargeTileBar") {
                            val view = chain.thisObject["mBarRootView"] as? View
                            view?.visibility = View.GONE
                        }
                        result
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        if (QsBar.MediaPlayer in qsBarSet && ONE_UI_VERSION < 80500) {
            try {
                val qsMediaPlayerBarClass = param.classLoader.loadClass(
                    "com.android.systemui.qs.bar.QSMediaPlayerBar"
                )
                val inflateViewsMethod = qsMediaPlayerBarClass.getDeclaredMethod(
                    "inflateViews",
                    ViewGroup::class.java
                )
                xposedModule.hook(inflateViewsMethod).intercept(hideBarCallback)
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        if (QsBar.SecurityFooter in qsBarSet) {
            try {
                if (ONE_UI_VERSION >= 80500) {
                    val bottomBannerTransformClass = runCatching {
                        param.classLoader.loadClass(
                            $$"com.android.systemui.samsung.quicksetting.ui.banner.BottomBannerViewModel$1$1"
                        )
                    }.getOrNull()
                    bottomBannerTransformClass?.declaredMethods
                        ?.filter { it.name == "invoke" }
                        ?.forEach { method ->
                            xposedModule.hook(method).intercept { chain ->
                                if (chain.args.getOrNull(1) is Boolean) {
                                    chain.args[1] = false
                                }
                                chain.proceed()
                            }
                        }
                } else if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    val barItemImplClass = param.classLoader.loadClass(
                        "com.android.systemui.qs.bar.BarItemImpl"
                    )
                    val showBarMethod = barItemImplClass.getDeclaredMethod(
                        "showBar",
                        Boolean::class.javaPrimitiveType
                    )
                    xposedModule.hook(showBarMethod).intercept { chain ->
                        val tag = chain.thisObject["TAG"]
                        if (tag == "SecurityFooterBar") {
                            chain.args[0] = false
                        }
                        chain.proceed()
                    }
                } else {
                    val securityFooterBarClass = param.classLoader.loadClass(
                        "com.android.systemui.qs.bar.SecurityFooterBar"
                    )
                    val onVisibilityChangedMethod = securityFooterBarClass.getDeclaredMethod(
                        "onVisibilityChanged",
                        Int::class.javaPrimitiveType
                    )
                    xposedModule.hook(onVisibilityChangedMethod).intercept(hideBarCallback)
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        if (QsBar.DataUsage in qsBarSet) {
            try {
                if (ONE_UI_VERSION >= 80500) {
                    val bottomBannerTransformClass = runCatching {
                        param.classLoader.loadClass(
                            $$"com.android.systemui.samsung.quicksetting.ui.banner.BottomBannerViewModel$1$1"
                        )
                    }.getOrNull()
                    bottomBannerTransformClass?.declaredMethods
                        ?.filter { it.name == "invoke" }
                        ?.forEach { method ->
                            xposedModule.hook(method).intercept { chain ->
                                if (chain.args.getOrNull(2) is Boolean) {
                                    chain.args[2] = false
                                }
                                chain.proceed()
                            }
                        }
                } else {
                    val dataUsageBarClass = param.classLoader.loadClass(
                        "com.android.systemui.qs.bar.DataUsageBar"
                    )
                    val isAvailableMethod = dataUsageBarClass.getDeclaredMethod("isAvailable")
                    xposedModule.hook(isAvailableMethod).intercept { false }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        if (QsBar.SmartViewAndModes in qsBarSet && ONE_UI_VERSION < 80500) {
            try {
                val barItemImplClass = param.classLoader.loadClass(
                    "com.android.systemui.qs.bar.BarItemImpl"
                )
                val showBarMethod = barItemImplClass.getDeclaredMethod(
                    "showBar",
                    Boolean::class.javaPrimitiveType
                )
                xposedModule.hook(showBarMethod).intercept { chain ->
                    val tag = chain.thisObject["TAG"]
                    if (tag == "SmartViewLargeTileBar") {
                        chain.args[0] = false
                    }
                    chain.proceed()
                }
            } catch (t: Throwable) {
                xlog(t)
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
            val topLargeTileBarClass = param.classLoader.loadClass(
                "com.android.systemui.qs.bar.TopLargeTileBar"
            )
            val tileRecordClass = param.classLoader.loadClass(
                $$"com.android.systemui.qs.SecQSPanelControllerBase$TileRecord"
            )
            val addTileMethod = topLargeTileBarClass.getDeclaredMethod("addTile", tileRecordClass)
            xposedModule.hook(addTileMethod).intercept { chain ->
                val tile = chain.args[0]?.get("tile")
                val tileSpec = tile?.callMethod("getTileSpec")
                val flag = when (tileSpec) {
                    "DeviceControl" if nearbyDevicesAndDeviceControl -> true
                    "custom(com.samsung.android.mydevice/.quicksettings.MyDeviceTileService)" if nearbyDevicesAndDeviceControl -> true
                    "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)" if smartViewAndModes -> true
                    "custom(com.samsung.android.app.routines/.LifestyleModeTile)" if smartViewAndModes -> true
                    else -> false
                }
                if (flag) {
                    null
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun alwaysExpandQsTileChunk() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
        ) return
        try {
            val tileChunkLayoutBarClass = param.classLoader.loadClass(
                "com.android.systemui.qs.bar.TileChunkLayoutBar"
            )
            val setContainerHeightMethod = tileChunkLayoutBarClass.getDeclaredMethod(
                "setContainerHeight",
                Int::class.javaPrimitiveType
            )
            xposedModule.hook(setContainerHeightMethod).intercept { chain ->
                val expandedHeight = chain.thisObject["mContainerExpandedHeight"] as? Int
                if (expandedHeight != null) {
                    chain.args[0] = expandedHeight
                }
                chain.proceed()
            }

            val inflateViewsMethod = tileChunkLayoutBarClass.getDeclaredMethod(
                "inflateViews",
                ViewGroup::class.java
            )
            xposedModule.hook(inflateViewsMethod).intercept { chain ->
                val result = chain.proceed()
                val scrollIndicator = chain.thisObject["mScrollIndicatorClickContainer"] as? View
                scrollIndicator?.visibility = View.GONE
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun alwaysShowTimeDateOnQs() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
        ) return
        try {
            val animatorClass = param.classLoader.loadClass(
                "com.android.systemui.qs.animator.SecQSFragmentAnimatorBase"
            )
            val qsClass = param.classLoader.loadClass("com.android.systemui.plugins.qs.QS")
            val setQsMethod = animatorClass.getDeclaredMethod("setQs", qsClass)
            xposedModule.hook(setQsMethod).intercept { chain ->
                val result = chain.proceed()
                if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    chain.thisObject["clockDateContainer"] = null
                } else {
                    val context = chain.thisObject["context"] as? Context
                    if (context != null) {
                        chain.thisObject["clockDateContainer"] = View(context)
                    }
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val callback = XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val mContext = chain.thisObject["mContext"] as? Context
                if (mContext != null) {
                    chain.thisObject["mClockDateContainer"] = View(mContext)
                }
                result
            }
            if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                val legacyAnimatorClass = param.classLoader.loadClass(
                    "com.android.systemui.qs.animator.LegacyQsExpandAnimator"
                )
                val method = legacyAnimatorClass.getDeclaredMethod($$"updateViews$2")
                xposedModule.hook(method).intercept(callback)
            } else {
                val expandAnimatorClass = param.classLoader.loadClass(
                    "com.android.systemui.qs.animator.QsExpandAnimator"
                )
                val method = expandAnimatorClass.getDeclaredMethod("updateViews")
                xposedModule.hook(method).intercept(callback)
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setQsClockStyle(
        monospaced: Boolean,
        modifyTextSize: Boolean,
        textSize: Float
    ) {
        if (param.packageName != Package.SYSTEMUI || (!monospaced && !modifyTextSize)) {
            return
        }
        // 布局在 res/layout/sec_qqs_date_buttons.xml
        try {
            val headerClass = param.classLoader.loadClass(
                "com.android.systemui.qs.SecQuickStatusBarHeader"
            )
            val onFinishInflateMethod = headerClass.getDeclaredMethod("onFinishInflate")
            xposedModule.hook(onFinishInflateMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val clockView = chain.thisObject["mClockView"] as? TextView
                    if (clockView != null) {
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
                            clockView.setPadding(
                                clockView.paddingLeft,
                                padding,
                                clockView.paddingRight,
                                padding
                            )
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
    fun supportOutdoorMode() {
        if (param.packageName != Package.SYSTEMUI) return

        fun outdoorModeRowTag() = "io.github.soclear.oneuix.outdoor_mode_row"

        fun isOutdoorModeEnabled(context: Context): Boolean {
            return (android.provider.Settings.System::class.java.callStaticMethod(
                "getIntForUser",
                context.contentResolver,
                "display_outdoor_mode",
                0,
                -2
            ) as Int) != 0
        }

        fun setOutdoorModeEnabled(context: Context, enabled: Boolean) {
            android.provider.Settings.System::class.java.callStaticMethod(
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
                val outdoorContainer = switchPreferenceClass.callStaticMethod(
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
                xlog(t)
            }
        }

        try {
            val switchPreferenceClass = param.classLoader.loadClass(
                "com.android.systemui.qs.SecQSSwitchPreference"
            )
            val brightnessDetailClass = runCatching {
                param.classLoader.loadClass("com.android.systemui.settings.brightness.BrightnessDetailAdapter")
            }.getOrNull() ?: runCatching {
                param.classLoader.loadClass($$"com.android.systemui.settings.brightness.BrightnessDetail$1")
            }.getOrNull()

            if (brightnessDetailClass != null) {
                val createDetailViewMethod = brightnessDetailClass.getDeclaredMethod(
                    "createDetailView",
                    Context::class.java,
                    View::class.java,
                    ViewGroup::class.java
                )
                xposedModule.hook(createDetailViewMethod).intercept { chain ->
                    val result = chain.proceed()
                    val context = chain.args[0] as Context
                    val detailView = result as? ViewGroup
                    if (detailView != null) {
                        addOutdoorModeRow(context, detailView, switchPreferenceClass)
                    }
                    result
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showTraditionalChineseDateOnQS() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            val qsShortenDateClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSShortenDate"
            )
            qsShortenDateClass.declaredConstructors.forEach { constructor ->
                xposedModule.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    val textView = chain.thisObject as? TextView
                    textView?.apply {
                        isSingleLine = false
                        setLines(2)
                        ellipsize = null
                        setPadding(paddingLeft, -10, paddingRight, -10)
                        setLineSpacing(0f, 0.8f)
                        val density = context.resources.displayMetrics.density
                        translationY = -10 * density
                    }
                    result
                }
            }

            val qsClockBellSoundClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockBellSound"
            )
            val notifyTimeChangedMethod = qsShortenDateClass.getDeclaredMethod(
                "notifyTimeChanged",
                qsClockBellSoundClass
            )
            var previousDate = ""
            var result = ""
            xposedModule.hook(notifyTimeChangedMethod).intercept { chain ->
                val shortDateText = chain.args[0]?.get("ShortDateText") as? String ?: ""
                if (shortDateText != previousDate) {
                    previousDate = shortDateText
                    result = "$shortDateText\n${TraditionalChineseCalendar.getMonthAndDay()}"
                }
                val dateTextView = chain.thisObject as? TextView
                if (dateTextView?.text != result) {
                    dateTextView?.text = result
                }
                null
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun addVolumeProgressToQsBar() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
        ) return
        var textView: TextView? = null

        try {
            val volumeBarClass = param.classLoader.loadClass("com.android.systemui.qs.bar.VolumeBar")
            val inflateViewsMethod = volumeBarClass.getDeclaredMethod("inflateViews", ViewGroup::class.java)
            xposedModule.hook(inflateViewsMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val slider = chain.thisObject["mSlider"] as? View
                    val sliderParent = slider?.parent as? FrameLayout
                    if (sliderParent != null) {
                        val volumeSeekBar = chain.thisObject["mVolumeSeekBar"]
                        val progress = volumeSeekBar?.get("progress") as? Int ?: 0
                        textView = TextView(sliderParent.context).apply {
                            setTextColor(Color.WHITE)
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
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }

            val listenerClass = param.classLoader.loadClass(
                $$"com.android.systemui.qs.bar.VolumeToggleSeekBar$VolumeSeekbarChangeListener"
            )
            val onProgressChangedMethod = listenerClass.getDeclaredMethod(
                "onProgressChanged",
                SeekBar::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            xposedModule.hook(onProgressChangedMethod).intercept { chain ->
                val result = chain.proceed()
                textView?.text = chain.args[1].toString()
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun addBrightnessProgressToQsBar() {
        if (param.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            ONE_UI_VERSION >= 80500
        ) return
        val textViewList = mutableListOf<TextView>()

        try {
            val controllerClass = param.classLoader.loadClass(
                "com.android.systemui.settings.brightness.BrightnessSliderController"
            )
            val onViewAttachedMethod = controllerClass.getDeclaredMethod("onViewAttached")
            xposedModule.hook(onViewAttachedMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val view = chain.thisObject["mView"]
                    val slider = view?.get("mSlider") as? View
                    val frameLayout = slider?.parent as? FrameLayout
                    if (frameLayout != null) {
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
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }

            val listenerClass = param.classLoader.loadClass(
                $$"com.android.systemui.settings.brightness.BrightnessSliderController$2"
            )
            val onProgressChangedMethod = listenerClass.getDeclaredMethod(
                "onProgressChanged",
                SeekBar::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            xposedModule.hook(onProgressChangedMethod).intercept { chain ->
                val result = chain.proceed()
                val progress = chain.args[1].toString()
                textViewList.forEach {
                    it.text = progress
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
