package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.get
import io.github.soclear.oneuix.hook.util.xlog

object WatchPairing {
    const val MODE_NONE = 0
    const val MODE_WEAROS_CN = 1
    const val MODE_WEAROS_GLOBAL = 2

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(
        bypassRegionCheck: Boolean,
        connectionMode: Int,
        supplementChinaWearOsGms: Boolean,
    ) {
        if (param.packageName != Package.WATCH_MANAGER) return

        xlog("OneUIX: WatchPairing init: bypassRegionCheck=$bypassRegionCheck, connectionMode=$connectionMode")

        if (bypassRegionCheck) {
            bypassRegionCheck()
            disableCscCheck()
            bypassPairingProblemCheck()
            spoofCscValue()
        }

        if (connectionMode != MODE_NONE) {
            spoofChinaEdition(connectionMode)
        }

        if (connectionMode == MODE_WEAROS_CN && supplementChinaWearOsGms) {
            supplementChinaWearOsGms()
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassRegionCheck() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.twatchmanager.connectionmanager.util.BluetoothUuidUtil")
                    .getDeclaredMethod(
                        "checkDeviceRegion",
                        android.content.Context::class.java,
                        android.bluetooth.BluetoothDevice::class.java
                    )
            ).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun spoofChinaEdition(connectionMode: Int) {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.global.utils.GoogleRequirementUtils")
                    .getDeclaredMethod("isChinaEdition", android.content.Context::class.java)
            ).intercept { chain ->
                when (connectionMode) {
                    MODE_WEAROS_CN -> true
                    MODE_WEAROS_GLOBAL -> false
                    else -> chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun disableCscCheck() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.global.utils.PlatformUtils")
                    .getDeclaredMethod("isSamsungChinaModel")
            ).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassPairingProblemCheck() {
        try {
            val problemClass = param.classLoader.loadClass(
                $$"com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker$Problem"
            )
            val wearableDeviceClass = param.classLoader.loadClass(
                "com.samsung.android.app.twatchmanager.connectionmanager.define.WearableDevice"
            )
            val bluetoothDeviceClass =
                param.classLoader.loadClass("android.bluetooth.BluetoothDevice")
            val fragmentActivityClass =
                param.classLoader.loadClass("androidx.fragment.app.FragmentActivity")

            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker")
                    .getDeclaredMethod(
                        "problemCheckAfterPairing",
                        wearableDeviceClass,
                        bluetoothDeviceClass,
                        Boolean::class.javaPrimitiveType,
                        fragmentActivityClass
                    )
            ).intercept { chain ->
                val result = chain.proceed()
                if (result?.toString() == "WEAR_OS_NOT_SUPPORTED_PHONE") {
                    problemClass["NO_PROBLEM"]
                } else {
                    result
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun supplementChinaWearOsGms() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.watchmanager.setupwizard.downloadinstall.HMConnectFragment")
                    .getDeclaredMethod("makePackageListToDownload")
            ).intercept { chain ->
                val result = chain.proceed()
                val packages = result as? Set<*> ?: return@intercept result
                val newPackages = packages.toMutableSet()
                newPackages.add("com.google.android.wearable.app.cn")
                newPackages
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun spoofCscValue() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.twatchmanager.util.PlatformNetworkUtils")
                    .getDeclaredMethod("getCSC")
            ).intercept { "TGY" }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
