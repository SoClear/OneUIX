package io.github.soclear.oneuix.hook

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findFieldIfExists
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

internal object HideBatteryIconHook {
    private const val STATUS_BAR_CHARGING_ICON = "stat_sys_battery_charging"
    private const val FALLBACK_CHARGING_ICON = "ic_icon_charging"
    private const val UNRESOLVED_CHARGING_ICON_ID = -1

    private var batteryChargingIconId = UNRESOLVED_CHARGING_ICON_ID
    private val applyingBatteryIconViews: MutableSet<Any> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    private val appliedBatteryChargingIconIds: MutableMap<ImageView, Int> =
        Collections.synchronizedMap(WeakHashMap())
    private val originalBatteryIconLayouts: MutableMap<ImageView, BatteryIconLayout> =
        Collections.synchronizedMap(WeakHashMap())
    private val originalBatteryMeterPaddings: MutableMap<View, ViewPadding> =
        Collections.synchronizedMap(WeakHashMap())
    private val originalBatteryMeterLayouts: MutableMap<View, BatteryIconLayout> =
        Collections.synchronizedMap(WeakHashMap())
    private val originalBatteryPercentLayouts: MutableMap<TextView, BatteryIconLayout> =
        Collections.synchronizedMap(WeakHashMap())

    fun apply(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                applyBatteryIconVisibility(param.thisObject)
            }
        }

        try {
            hookAllConstructors(
                findClass(
                    "com.android.systemui.battery.BatteryMeterView",
                    loadPackageParam.classLoader
                ),
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        listOf(
            "onFinishInflate",
            "onAttachedToWindow",
            "onBatteryLevelChanged",
            "updatePercentText",
            "updateShowPercent",
            "scaleBatteryMeterViewsLegacy"
        ).forEach { methodName ->
            try {
                hookAllMethods(
                    findClass(
                        "com.android.systemui.battery.BatteryMeterView",
                        loadPackageParam.classLoader
                    ),
                    methodName,
                    callback
                )
            } catch (_: Throwable) {
            }
        }

        try {
            hookAllMethods(
                findClass(
                    "com.android.systemui.battery.BatteryMeterViewController\$3",
                    loadPackageParam.classLoader
                ),
                "onBatteryLevelChanged",
                callback
            )
        } catch (_: Throwable) {
        }

        try {
            findAndHookMethod(
                "com.android.systemui.battery.BatteryMeterView",
                loadPackageParam.classLoader,
                "updateColors",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val batteryMeterView = resolveBatteryMeterView(param.thisObject) ?: return
                        if (!isBatteryCharging(batteryMeterView)) return
                        val iconView = readFieldValue(
                            batteryMeterView,
                            listOf("mBatteryIconView", "batteryIconView")
                        ) as? ImageView ?: return
                        applyBatteryChargingIconTint(batteryMeterView, iconView)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun applyBatteryIconVisibility(instance: Any) {
        val batteryMeterView = resolveBatteryMeterView(instance) ?: return
        if (!applyingBatteryIconViews.add(batteryMeterView)) return
        try {
            val view = batteryMeterView as? View ?: return
            val iconView = readFieldValue(
                batteryMeterView,
                listOf("mBatteryIconView", "batteryIconView")
            ) as? ImageView ?: return
            val percentView = readFieldValue(
                batteryMeterView,
                listOf("mBatteryPercentView", "batteryPercentView")
            ) as? TextView

            if (isBatteryCharging(batteryMeterView)) {
                restoreBatteryIconLayout(iconView)
                restoreBatteryMeterPadding(view)
                restoreBatteryMeterLayout(view)
                percentView?.let(::restoreBatteryPercentLayout)
                setBatteryChargingIcon(iconView)
                applyBatteryChargingIconTint(batteryMeterView, iconView)
                iconView.visibility = View.VISIBLE
                view.visibility = View.VISIBLE
                return
            }

            collapseBatteryIconLayout(iconView)
            iconView.visibility = View.GONE
            view.visibility = if (percentView?.isVisibleWithText() == true) {
                collapseBatteryMeterEndPadding(view)
                collapseBatteryMeterEndSpacing(view)
                collapseBatteryPercentEndSpacing(percentView)
                View.VISIBLE
            } else {
                View.GONE
            }
        } finally {
            applyingBatteryIconViews.remove(batteryMeterView)
        }
    }

    private fun resolveBatteryMeterView(instance: Any): Any? {
        if (instance is View) return instance
        val controller = readFieldValue(instance, listOf("this\$0")) ?: return null
        return readFieldValue(controller, listOf("mView"))
    }

    private fun isBatteryCharging(batteryMeterView: Any): Boolean {
        val meterCharging = readFieldValue(
            batteryMeterView,
            listOf("mCharging", "charging")
        ) as? Boolean == true
        if (meterCharging) return true

        val samsungDrawable = readFieldValue(
            batteryMeterView,
            listOf("mSamsungDrawable", "samsungDrawable")
        ) ?: return false
        val batteryState = readFieldValue(
            samsungDrawable,
            listOf("batteryState", "mBatteryState")
        ) ?: return false
        return readFieldValue(batteryState, listOf("charging")) as? Boolean == true ||
            readFieldValue(batteryState, listOf("isDirectPowerMode")) as? Boolean == true
    }

    private fun setBatteryChargingIcon(iconView: ImageView) {
        val drawableId = resolveBatteryChargingIconId(iconView)
        if (drawableId == 0 || appliedBatteryChargingIconIds[iconView] == drawableId) return
        iconView.setImageResource(drawableId)
        appliedBatteryChargingIconIds[iconView] = drawableId
    }

    private fun resolveBatteryChargingIconId(iconView: ImageView): Int {
        if (batteryChargingIconId != UNRESOLVED_CHARGING_ICON_ID) return batteryChargingIconId
        val resources = iconView.resources
        batteryChargingIconId = resources.getIdentifier(
            STATUS_BAR_CHARGING_ICON,
            "drawable",
            Package.SYSTEMUI
        ).takeIf { it != 0 } ?: resources.getIdentifier(
            FALLBACK_CHARGING_ICON,
            "drawable",
            Package.SYSTEMUI
        )
        return batteryChargingIconId
    }

    private fun applyBatteryChargingIconTint(batteryMeterView: Any, iconView: ImageView) {
        val samsungDrawable = readFieldValue(
            batteryMeterView,
            listOf("mSamsungDrawable", "samsungDrawable")
        ) ?: return
        val iconTint = readFieldValue(samsungDrawable, listOf("iconTint")) as? Int ?: return
        iconView.setColorFilter(iconTint)
    }

    private fun collapseBatteryIconLayout(iconView: ImageView) {
        rememberBatteryIconLayout(iconView)
        val params = iconView.layoutParams ?: return
        params.width = 0
        params.height = 0
        (params as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, 0)
        iconView.layoutParams = params
    }

    private fun restoreBatteryIconLayout(iconView: ImageView) {
        val original = originalBatteryIconLayouts[iconView] ?: return
        val params = iconView.layoutParams ?: return
        params.width = original.width
        params.height = original.height
        (params as? ViewGroup.MarginLayoutParams)?.setMargins(
            original.leftMargin,
            original.topMargin,
            original.rightMargin,
            original.bottomMargin
        )
        iconView.layoutParams = params
        iconView.setPaddingRelative(
            original.paddingStart,
            original.paddingTop,
            original.paddingEnd,
            original.paddingBottom
        )
    }

    private fun collapseBatteryMeterEndPadding(view: View) {
        rememberBatteryMeterPadding(view)
        view.setPaddingRelative(
            view.paddingStart,
            view.paddingTop,
            0,
            view.paddingBottom
        )
    }

    private fun restoreBatteryMeterPadding(view: View) {
        val original = originalBatteryMeterPaddings[view] ?: return
        view.setPaddingRelative(
            original.start,
            original.top,
            original.end,
            original.bottom
        )
    }

    private fun rememberBatteryMeterPadding(view: View) {
        if (originalBatteryMeterPaddings.containsKey(view)) return
        originalBatteryMeterPaddings[view] = ViewPadding(
            start = view.paddingStart,
            top = view.paddingTop,
            end = view.paddingEnd,
            bottom = view.paddingBottom
        )
    }

    private fun collapseBatteryMeterEndSpacing(view: View) {
        rememberBatteryMeterLayout(view)
        val params = view.layoutParams
        (params as? ViewGroup.MarginLayoutParams)?.let {
            it.marginEnd = 0
            it.rightMargin = 0
            view.layoutParams = it
        }
    }

    private fun restoreBatteryMeterLayout(view: View) {
        val original = originalBatteryMeterLayouts[view] ?: return
        val params = view.layoutParams ?: return
        params.width = original.width
        params.height = original.height
        (params as? ViewGroup.MarginLayoutParams)?.setMargins(
            original.leftMargin,
            original.topMargin,
            original.rightMargin,
            original.bottomMargin
        )
        view.layoutParams = params
    }

    private fun rememberBatteryMeterLayout(view: View) {
        if (originalBatteryMeterLayouts.containsKey(view)) return
        val params = view.layoutParams ?: return
        val marginParams = params as? ViewGroup.MarginLayoutParams
        originalBatteryMeterLayouts[view] = BatteryIconLayout(
            width = params.width,
            height = params.height,
            leftMargin = marginParams?.leftMargin ?: 0,
            topMargin = marginParams?.topMargin ?: 0,
            rightMargin = marginParams?.rightMargin ?: 0,
            bottomMargin = marginParams?.bottomMargin ?: 0,
            paddingStart = view.paddingStart,
            paddingTop = view.paddingTop,
            paddingEnd = view.paddingEnd,
            paddingBottom = view.paddingBottom
        )
    }

    private fun collapseBatteryPercentEndSpacing(percentView: TextView) {
        rememberBatteryPercentLayout(percentView)
        val params = percentView.layoutParams
        (params as? ViewGroup.MarginLayoutParams)?.let {
            it.marginEnd = 0
            it.rightMargin = 0
            percentView.layoutParams = it
        }
        percentView.setPaddingRelative(
            percentView.paddingStart,
            percentView.paddingTop,
            0,
            percentView.paddingBottom
        )
    }

    private fun restoreBatteryPercentLayout(percentView: TextView) {
        val original = originalBatteryPercentLayouts[percentView] ?: return
        val params = percentView.layoutParams ?: return
        params.width = original.width
        params.height = original.height
        (params as? ViewGroup.MarginLayoutParams)?.setMargins(
            original.leftMargin,
            original.topMargin,
            original.rightMargin,
            original.bottomMargin
        )
        percentView.layoutParams = params
        percentView.setPaddingRelative(
            original.paddingStart,
            original.paddingTop,
            original.paddingEnd,
            original.paddingBottom
        )
    }

    private fun rememberBatteryPercentLayout(percentView: TextView) {
        if (originalBatteryPercentLayouts.containsKey(percentView)) return
        val params = percentView.layoutParams ?: return
        val marginParams = params as? ViewGroup.MarginLayoutParams
        originalBatteryPercentLayouts[percentView] = BatteryIconLayout(
            width = params.width,
            height = params.height,
            leftMargin = marginParams?.leftMargin ?: 0,
            topMargin = marginParams?.topMargin ?: 0,
            rightMargin = marginParams?.rightMargin ?: 0,
            bottomMargin = marginParams?.bottomMargin ?: 0,
            paddingStart = percentView.paddingStart,
            paddingTop = percentView.paddingTop,
            paddingEnd = percentView.paddingEnd,
            paddingBottom = percentView.paddingBottom
        )
    }

    private fun rememberBatteryIconLayout(iconView: ImageView) {
        if (originalBatteryIconLayouts.containsKey(iconView)) return
        val params = iconView.layoutParams ?: return
        if (params.width == 0 && params.height == 0) return
        val marginParams = params as? ViewGroup.MarginLayoutParams
        originalBatteryIconLayouts[iconView] = BatteryIconLayout(
            width = params.width,
            height = params.height,
            leftMargin = marginParams?.leftMargin ?: 0,
            topMargin = marginParams?.topMargin ?: 0,
            rightMargin = marginParams?.rightMargin ?: 0,
            bottomMargin = marginParams?.bottomMargin ?: 0,
            paddingStart = iconView.paddingStart,
            paddingTop = iconView.paddingTop,
            paddingEnd = iconView.paddingEnd,
            paddingBottom = iconView.paddingBottom
        )
    }

    private fun TextView.isVisibleWithText(): Boolean =
        visibility == View.VISIBLE && text.isNotBlank()

    private data class BatteryIconLayout(
        val width: Int,
        val height: Int,
        val leftMargin: Int,
        val topMargin: Int,
        val rightMargin: Int,
        val bottomMargin: Int,
        val paddingStart: Int,
        val paddingTop: Int,
        val paddingEnd: Int,
        val paddingBottom: Int
    )

    private data class ViewPadding(
        val start: Int,
        val top: Int,
        val end: Int,
        val bottom: Int
    )

    private fun readFieldValue(instance: Any, names: List<String>): Any? =
        findField(instance, names)?.let { field -> runCatching { field.get(instance) }.getOrNull() }

    private fun findField(instance: Any, names: List<String>): Field? {
        names.forEach { name ->
            findFieldIfExists(instance.javaClass, name)?.let { return it }
        }
        return null
    }
}
