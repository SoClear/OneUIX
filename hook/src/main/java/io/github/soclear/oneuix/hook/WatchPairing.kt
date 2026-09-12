package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object WatchPairing {
    const val MODE_NONE = 0
    const val MODE_WEAROS_CN = 1
    const val MODE_WEAROS_GLOBAL = 2

    fun init(
        lpparam: LoadPackageParam,
        bypassRegionCheck: Boolean,
        connectionMode: Int,
        supplementChinaWearOsGms: Boolean,
    ) {
        if (lpparam.packageName != Package.WATCH_MANAGER) return

        XposedBridge.log("OneUIX: WatchPairing init: bypassRegionCheck=$bypassRegionCheck, connectionMode=$connectionMode")

        if (bypassRegionCheck) {
            bypassRegionCheck(lpparam)
            disableCscCheck(lpparam)
            bypassPairingProblemCheck(lpparam)
            spoofCscValue(lpparam)
        }

        if (connectionMode != MODE_NONE) {
            spoofChinaEdition(lpparam, connectionMode)
        }

        if (connectionMode == MODE_WEAROS_CN && supplementChinaWearOsGms) {
            supplementChinaWearOsGms(lpparam)
        }
    }

    private fun bypassRegionCheck(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.twatchmanager.connectionmanager.util.BluetoothUuidUtil",
                lpparam.classLoader,
                "checkDeviceRegion",
                android.content.Context::class.java,
                android.bluetooth.BluetoothDevice::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun spoofChinaEdition(lpparam: LoadPackageParam, connectionMode: Int) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.global.utils.GoogleRequirementUtils",
                lpparam.classLoader,
                "isChinaEdition",
                android.content.Context::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        when (connectionMode) {
                            MODE_WEAROS_CN -> param.result = true
                            MODE_WEAROS_GLOBAL -> param.result = false
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun disableCscCheck(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.global.utils.PlatformUtils",
                lpparam.classLoader,
                "isSamsungChinaModel",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return false
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun bypassPairingProblemCheck(lpparam: LoadPackageParam) {
        try {
            val problemClass = XposedHelpers.findClass(
                $$"com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker$Problem",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker",
                lpparam.classLoader,
                "problemCheckAfterPairing",
                "com.samsung.android.app.twatchmanager.connectionmanager.define.WearableDevice",
                "android.bluetooth.BluetoothDevice",
                Boolean::class.javaPrimitiveType,
                "androidx.fragment.app.FragmentActivity",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result?.toString() == "WEAR_OS_NOT_SUPPORTED_PHONE") {
                            param.result = XposedHelpers.getStaticObjectField(
                                problemClass,
                                "NO_PROBLEM"
                            )
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun supplementChinaWearOsGms(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.watchmanager.setupwizard.downloadinstall.HMConnectFragment",
                lpparam.classLoader,
                "makePackageListToDownload",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val packages = param.result as? Set<*> ?: return
                        val newPackages = packages.toMutableSet()
                        newPackages.add("com.google.android.wearable.app.cn")
                        param.result = newPackages
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun spoofCscValue(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.twatchmanager.util.PlatformNetworkUtils",
                lpparam.classLoader,
                "getCSC",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return "TGY"
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
