package io.github.soclear.oneuix.hook

import android.graphics.Typeface
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getSurroundingThis
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import java.net.NetworkInterface
import kotlin.math.roundToInt


object Network {
    fun supportRealTimeNetworkSpeed(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS &&
            loadPackageParam.packageName != Package.SYSTEMUI
        ) {
            return
        }
        try {
            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getBoolean",
                String::class.java,
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "CscFeature_Common_SupportZProjectFunctionInGlobal") {
                            param.result = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun showSeparateUpDownNetworkSpeeds(
        loadPackageParam: LoadPackageParam,
        intervalMillisecond: Long = 3000L,
        thresholdKb: Int = 0
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || intervalMillisecond <= 0L) {
            return
        }
        val netSpeedViewString = "${Package.SYSTEMUI}.statusbar.policy.NetspeedView"
        val controllerString = "${netSpeedViewString}Controller"

        data class NetworkStats(val totalTx: Long, val totalRx: Long, val interfaces: Set<String>)

        @Suppress("DEPRECATION")
        val callback = object : XC_MethodReplacement() {
            private val MESSAGE_INITIAL = 1
            private val MESSAGE_UPDATE = 2

            private var lastNetworkStats = NetworkStats(0L, 0L, emptySet())
            private var lastUpdateTime: Long = 0L

            private fun Handler.scheduleNextUpdate() {
                sendEmptyMessageDelayed(MESSAGE_UPDATE, intervalMillisecond)
            }

            private fun getCurrentNetworkStats(): NetworkStats {
                var totalTx = 0L
                var totalRx = 0L
                val validInterfaces = mutableSetOf<String>()
                try {
                    // 获取设备上所有的网络接口
                    val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                    while (networkInterfaces.hasMoreElements()) {
                        val networkInterface = networkInterfaces.nextElement()
                        // 排除本地回环接口(lo)和VPN虚拟接口(tun)
                        if (networkInterface.isUp &&
                            !networkInterface.isVirtual &&
                            !networkInterface.isLoopback &&
                            !networkInterface.name.startsWith("tun") &&
                            !networkInterface.name.startsWith("dummy")
                        ) {
                            totalTx += TrafficStats.getTxBytes(networkInterface.name)
                            totalRx += TrafficStats.getRxBytes(networkInterface.name)
                            validInterfaces.add(networkInterface.name)
                        }
                    }
                } catch (t: Throwable) {
                    // 如果出错，回退到可能不准的方法，但至少不会崩溃
                    XposedBridge.log("Error getting specific network stats, falling back to total: ${t.message}")
                    return NetworkStats(
                        TrafficStats.getMobileTxBytes(),
                        TrafficStats.getTotalRxBytes(),
                        emptySet()
                    )
                }
                return NetworkStats(totalTx, totalRx, validInterfaces)
            }

            private fun calculateSpeedString(
                current: NetworkStats,
                previous: NetworkStats,
                actualIntervalSeconds: Float
            ): String {
                val txBytesPerSecond = (current.totalTx - previous.totalTx) / actualIntervalSeconds
                val rxBytesPerSecond = (current.totalRx - previous.totalRx) / actualIntervalSeconds
                if (!shouldDisplayNetworkSpeed(txBytesPerSecond, rxBytesPerSecond, thresholdKb)) {
                    return ""
                }
                return "${formatSpeed(txBytesPerSecond)}\n${formatSpeed(rxBytesPerSecond)}"
            }

            // 格式化网速，speed 为每秒字节数
            private fun formatSpeed(bytesPerSecond: Float): String {
                // 0 或负数显示为 "0B"
                if (bytesPerSecond <= 0f) {
                    return "0B"
                }
                if (bytesPerSecond < 1024f) {
                    return "${bytesPerSecond.roundToInt()}B"
                }
                val kiBytesPerSecond = bytesPerSecond / 1024f
                if (kiBytesPerSecond < 100f) {
                    return "%.2fK".format(kiBytesPerSecond)
                }
                if (kiBytesPerSecond < 1000f) {
                    return "%.1fK".format(kiBytesPerSecond)
                }
                val miBytesPerSecond = kiBytesPerSecond / 1024f
                if (miBytesPerSecond < 100f) {
                    return "%.2fM".format(miBytesPerSecond)
                }
                return "%.1fM".format(miBytesPerSecond)
            }

            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                try {
                    val message = param.args[0] as Message
                    val handler = param.thisObject as Handler
                    val observable = getSurroundingThis(param.thisObject) as java.util.Observable
                    if (observable.countObservers() <= 0) return null

                    when (message.what) {
                        MESSAGE_INITIAL -> {
                            lastNetworkStats = getCurrentNetworkStats()
                            lastUpdateTime = SystemClock.elapsedRealtime()
                            handler.scheduleNextUpdate()
                        }

                        MESSAGE_UPDATE -> {
                            val currentNetworkStats = getCurrentNetworkStats()
                            val currentTime = SystemClock.elapsedRealtime()
                            if (currentNetworkStats.interfaces == lastNetworkStats.interfaces &&
                                currentNetworkStats.totalTx >= lastNetworkStats.totalTx &&
                                currentNetworkStats.totalRx >= lastNetworkStats.totalRx &&
                                currentTime > lastUpdateTime
                            ) {
                                val actualIntervalSeconds = (currentTime - lastUpdateTime) / 1000f
                                val speedString = calculateSpeedString(
                                    currentNetworkStats,
                                    lastNetworkStats,
                                    actualIntervalSeconds
                                )

                                callMethod(observable, "setChanged")
                                observable.notifyObservers(speedString)
                            }

                            lastNetworkStats = currentNetworkStats
                            lastUpdateTime = currentTime
                            handler.scheduleNextUpdate()
                        }
                    }
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
                return null
            }
        }
        try {
            findAndHookMethod(
                $$"$$controllerString$NetworkSpeedManager$1",
                loadPackageParam.classLoader,
                "handleMessage",
                Message::class.java,
                callback
            )
            findAndHookMethod(
                netSpeedViewString,
                loadPackageParam.classLoader,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (getObjectField(param.thisObject, "mContentView") as TextView).apply {
                            setLines(2)
                            gravity = Gravity.END
                            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun turnOn5gQsTile(loadPackageParam: LoadPackageParam) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        if (loadPackageParam.packageName == Package.SYSTEMUI ||
            loadPackageParam.packageName == Package.TELEPHONYUI
        ) {
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args[0] != "CscFeature_SystemUI_ConfigDefQuickSettingItem") return
                    val quickSettingItem = param.result as String
                    if (!quickSettingItem.contains("TurnOn5g")) {
                        param.result = "$quickSettingItem,TurnOn5g"
                    }
                }
            }

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
        }
        if (loadPackageParam.packageName == Package.SYSTEMUI) {
            findAndHookMethod(
                "com.android.systemui.qs.QSTileHost",
                loadPackageParam.classLoader,
                "isAvailableCustomTile",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "TurnOn5g") {
                            param.result = true
                        }
                    }
                }
            )
        }
    }

    fun showWiFiLinkSpeed(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SETTINGS) {
            return
        }
        try {
            val connectedListAdapterClass = findClass(
                "com.samsung.android.settings.wifi.ConnectedListAdapter",
                loadPackageParam.classLoader
            )
            val viewHolderClass = findClass(
                "androidx.recyclerview.widget.RecyclerView.ViewHolder",
                loadPackageParam.classLoader
            )
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val linkSpeed = getLinkSpeed(param) ?: return
                    val mSummary = getObjectField(param.args[0], "mSummary") as TextView
                    mSummary.append(" $linkSpeed")
                }

                private fun getLinkSpeed(param: MethodHookParam): String? {
                    val wifiEntries = getObjectField(param.thisObject, "mWifiEntries") as List<*>
                    val wifiEntry = wifiEntries[param.args[1] as Int] ?: return null
                    val wifiInfo = getObjectField(wifiEntry, "mWifiInfo") as WifiInfo
                    return "${wifiInfo.txLinkSpeedMbps},${wifiInfo.rxLinkSpeedMbps}"
                }
            }
            findAndHookMethod(
                connectedListAdapterClass,
                "onBindViewHolder",
                viewHolderClass,
                Int::class.java,
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}

internal fun shouldDisplayNetworkSpeed(
    txBytesPerSecond: Float,
    rxBytesPerSecond: Float,
    thresholdKb: Int,
): Boolean {
    if (thresholdKb <= 0) return true
    val thresholdBytesPerSecond = thresholdKb * 1024f
    return txBytesPerSecond > thresholdBytesPerSecond ||
        rxBytesPerSecond > thresholdBytesPerSecond
}
