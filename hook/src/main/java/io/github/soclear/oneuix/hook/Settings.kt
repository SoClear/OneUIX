package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.get
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object Settings {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showPackageInfo() {
        if (param.packageName != Package.SETTINGS) return
        try {
            val controllerClass = param.classLoader.loadClass(
                "com.android.settings.applications.appinfo.AppHeaderViewPreferenceController"
            )
            val appEntryClass = param.classLoader.loadClass(
                $$"com.android.settingslib.applications.ApplicationsState$AppEntry"
            )
            val method = controllerClass.getDeclaredMethod(
                "setAppLabelAndIcon",
                PackageInfo::class.java,
                appEntryClass
            )
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    val header = chain.thisObject["mHeader"]
                    val mRootView = header?.get("mRootView") as? View ?: return@intercept result

                    @SuppressLint("DiscouragedApi")
                    val identifier = mRootView.resources.getIdentifier(
                        "entity_header_summary", "id", Package.SETTINGS
                    )
                    val packageInfo = chain.args[0] as PackageInfo
                    val versionName = packageInfo.versionName
                    val versionCode = packageInfo.longVersionCode
                    val packageName = packageInfo.packageName
                    mRootView.findViewById<TextView>(identifier)?.apply {
                        @SuppressLint("SetTextI18n")
                        text = "$text $versionName ($versionCode)\n$packageName"
                        setTextIsSelectable(true)
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

    // 支持任意字体
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportAnyFont() {
        if (param.packageName != Package.SETTINGS) return
        try {
            val clazz = param.classLoader.loadClass(
                "com.samsung.android.settings.display.SecDisplayUtils"
            )
            val method = clazz.getDeclaredMethod("isInvalidFont", Context::class.java, String::class.java)
            xposedModule.hook(method).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showMoreBatteryInfo() {
        // res/xml/sec_battery_info_settings.xml
        // com.samsung.android.settings.deviceinfo.batteryinfo
        if (param.packageName != Package.SETTINGS) return
        try {
            val clazz = param.classLoader.loadClass(
                "com.samsung.android.settings.deviceinfo.batteryinfo.BatteryRegulatoryPreferenceController"
            )
            val method = clazz.getDeclaredMethod("getAvailabilityStatus")
            xposedModule.hook(method).intercept { 0 }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showForcePeakRefreshRatePreference() {
        if (param.packageName != Package.SETTINGS) return
        try {
            val clazz = param.classLoader.loadClass(
                "com.android.settings.development.ForcePeakRefreshRatePreferenceController"
            )
            val method = clazz.getDeclaredMethod("isAvailable")
            xposedModule.hook(method).intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportOutdoorMode() {
        if (param.packageName != Package.SETTINGS) return
        try {
            val controllerClass = param.classLoader.loadClass(
                "com.samsung.android.settings.display.controller.SecOutDoorModePreferenceController"
            )
            val isAvailableMethod = controllerClass.getDeclaredMethod("isAvailable")
            xposedModule.hook(isAvailableMethod).intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val runeClass = runCatching {
                param.classLoader.loadClass("com.samsung.android.settings.Rune")
            }.getOrNull()
            val method = runeClass?.declaredMethods?.firstOrNull {
                it.name == "supportOutdoorMode" && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
            }
            if (method != null) {
                xposedModule.hook(method).intercept { true }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportAutoPowerOnOff() {
        if (param.packageName != Package.SETTINGS) return

        try {
            val floatingFeatureClass = param.classLoader.loadClass("com.samsung.android.feature.SemFloatingFeature")
            val getBooleanMethod = floatingFeatureClass.getDeclaredMethod("getBoolean", String::class.java)
            xposedModule.hook(getBooleanMethod).intercept { chain ->
                if (chain.args.firstOrNull() == "SEC_FLOATING_FEATURE_SETTINGS_SUPPORT_AUTO_POWER_ON_OFF") {
                    true
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val controllerClass = param.classLoader.loadClass(
                "com.samsung.android.settings.general.AutoPowerOnOffPreferenceController"
            )
            val isSupportMethod = controllerClass.getDeclaredMethod("isSupportAutoPowerOnOff")
            xposedModule.hook(isSupportMethod).intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }

        val shouldSpoofChinaModel = ThreadLocal<Boolean>().apply { set(false) }

        try {
            val receiverClass = param.classLoader.loadClass(
                "com.samsung.android.settings.autopoweronoff.AutoPowerOnOffReceiver"
            )
            val onReceiveMethod = receiverClass.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
            xposedModule.hook(onReceiveMethod).intercept { chain ->
                shouldSpoofChinaModel.set(true)
                try {
                    chain.proceed()
                } finally {
                    shouldSpoofChinaModel.set(false)
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val innerSettingsClass = param.classLoader.loadClass(
                $$"com.samsung.android.settings.autopoweronoff.AutoPowerOnOffSettings$2"
            )
            val resetSettingsMethod = innerSettingsClass.getDeclaredMethod("resetSettings", Context::class.java)
            xposedModule.hook(resetSettingsMethod).intercept { chain ->
                shouldSpoofChinaModel.set(true)
                try {
                    chain.proceed()
                } finally {
                    shouldSpoofChinaModel.set(false)
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val runeClass = param.classLoader.loadClass("com.samsung.android.settings.Rune")
            runeClass.declaredMethods
                .filter { it.name == "isChinaModel" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        if (shouldSpoofChinaModel.get() == true) {
                            true
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
    fun spoofPhoneStatusAsOfficial() {
        if (param.packageName != Package.SETTINGS) return
        try {
            val clazz = param.classLoader.loadClass("com.samsung.android.settings.deviceinfo.SecDeviceInfoUtils")
            val isPhoneStatusUnlockedMethod = clazz.getDeclaredMethod("isPhoneStatusUnlocked")
            xposedModule.hook(isPhoneStatusUnlockedMethod).intercept { false }

            val checkRootingConditionMethod = clazz.getDeclaredMethod("checkRootingCondition")
            xposedModule.hook(checkRootingConditionMethod).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
