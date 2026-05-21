package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package


object Settings {
    fun showPackageInfo(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS) return
        val callback = object : XC_MethodHook() {
            @SuppressLint("DiscouragedApi", "SetTextI18n")
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val header = getObjectField(param.thisObject, "mHeader")
                    val mRootView = getObjectField(header, "mRootView") as View
                    val identifier = mRootView.resources.getIdentifier(
                        "entity_header_summary", "id", Package.SETTINGS
                    )
                    val packageInfo = param.args[0] as PackageInfo
                    val versionName = packageInfo.versionName
                    val versionCode = packageInfo.longVersionCode
                    val packageName = packageInfo.packageName
                    mRootView.findViewById<TextView>(identifier).apply {
                        text = "$text $versionName ($versionCode)\n$packageName"
                        setTextIsSelectable(true)
                    }
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }
        try {
            findAndHookMethod(
                "com.android.settings.applications.appinfo.AppHeaderViewPreferenceController",
                loadPackageParam.classLoader,
                "setAppLabelAndIcon",
                PackageInfo::class.java,
                "com.android.settingslib.applications.ApplicationsState\$AppEntry",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    // 支持任意字体
    fun supportAnyFont(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS) return
        try {
            findAndHookMethod(
                "com.samsung.android.settings.display.SecDisplayUtils",
                loadPackageParam.classLoader,
                "isInvalidFont",
                Context::class.java,
                String::class.java,
                returnConstant(false)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun showMoreBatteryInfo(loadPackageParam: LoadPackageParam) {
        // res/xml/sec_battery_info_settings.xml
        // com.samsung.android.settings.deviceinfo.batteryinfo
        if (loadPackageParam.packageName != Package.SETTINGS) return
        try {
            findAndHookMethod(
                "com.samsung.android.settings.deviceinfo.batteryinfo.BatteryRegulatoryPreferenceController",
                loadPackageParam.classLoader,
                "getAvailabilityStatus",
                returnConstant(0)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun showForcePeakRefreshRatePreference(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS) return
        try {
            findAndHookMethod(
                "com.android.settings.development.ForcePeakRefreshRatePreferenceController",
                loadPackageParam.classLoader,
                "isAvailable",
                returnConstant(true)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun supportOutdoorMode(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS) return
        try {
            findAndHookMethod(
                "com.samsung.android.settings.display.controller.SecOutDoorModePreferenceController",
                loadPackageParam.classLoader,
                "isAvailable",
                returnConstant(true)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            findAndHookMethod(
                "com.samsung.android.settings.Rune",
                loadPackageParam.classLoader,
                "supportOutdoorMode",
                Context::class.java,
                returnConstant(true)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun supportAutoPowerOnOff(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS) return

        findAndHookMethod(
            "com.samsung.android.feature.SemFloatingFeature",
            loadPackageParam.classLoader,
            "getBoolean",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == "SEC_FLOATING_FEATURE_SETTINGS_SUPPORT_AUTO_POWER_ON_OFF") {
                        param.result = true
                    }
                }
            }
        )

        findAndHookMethod(
            "com.samsung.android.settings.general.AutoPowerOnOffPreferenceController",
            loadPackageParam.classLoader,
            "isSupportAutoPowerOnOff",
            returnConstant(true)
        )

        val shouldSpoofChinaModel = ThreadLocal<Boolean>().apply { set(false) }

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                shouldSpoofChinaModel.set(true)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                shouldSpoofChinaModel.set(false)
            }
        }

        findAndHookMethod(
            "com.samsung.android.settings.autopoweronoff.AutoPowerOnOffReceiver",
            loadPackageParam.classLoader,
            "onReceive",
            Context::class.java,
            Intent::class.java,
            callback
        )

        findAndHookMethod(
            "com.samsung.android.settings.autopoweronoff.AutoPowerOnOffSettings$2",
            loadPackageParam.classLoader,
            "resetSettings",
            Context::class.java,
            callback
        )

        findAndHookMethod(
            "com.samsung.android.settings.Rune",
            loadPackageParam.classLoader,
            "isChinaModel",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (shouldSpoofChinaModel.get() == true) {
                        param.setResult(true)
                    }
                }
            }
        )
    }
}
