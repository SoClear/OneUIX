package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
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
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.net.NetworkInterface
import kotlin.math.roundToInt

object Network {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportRealTimeNetworkSpeed() {
        if (param.packageName != Package.SETTINGS &&
            param.packageName != Package.SYSTEMUI
        ) {
            return
        }
        try {
            val semCscFeatureClass =
                param.classLoader.loadClass("com.samsung.android.feature.SemCscFeature")
            val method = semCscFeatureClass.getDeclaredMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            xposedModule.hook(method).intercept { chain ->
                if (chain.args.firstOrNull() == "CscFeature_Common_SupportZProjectFunctionInGlobal") {
                    true
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    @SuppressLint("PrivateApi")
    @Suppress("DEPRECATION")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showSeparateUpDownNetworkSpeeds(
        intervalMillisecond: Long = 3000L,
        thresholdKb: Int = 0
    ) = afterAttach {
        if (param.packageName != Package.SYSTEMUI || intervalMillisecond <= 0L) {
            return@afterAttach
        }
        val netSpeedViewString = "${Package.SYSTEMUI}.statusbar.policy.NetspeedView"
        val controllerString = "${netSpeedViewString}Controller"

        data class NetworkStats(val totalTx: Long, val totalRx: Long, val interfaces: Set<String>)

        val messageInitial = 1
        val messageUpdate = 2

        var lastNetworkStats = NetworkStats(0L, 0L, emptySet())
        var lastUpdateTime = 0L

        fun Handler.scheduleNextUpdate() {
            sendEmptyMessageDelayed(messageUpdate, intervalMillisecond)
        }

        fun getCurrentNetworkStats(): NetworkStats {
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
                xlog(t)
            }
            return NetworkStats(totalTx, totalRx, validInterfaces)
        }

        // 格式化网速，speed 为每秒字节数
        fun formatSpeed(bytesPerSecond: Float): String {
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

        fun shouldDisplayNetworkSpeed(
            txBytesPerSecond: Float,
            rxBytesPerSecond: Float,
            thresholdKb: Int,
        ): Boolean {
            if (thresholdKb <= 0) return true
            val thresholdBytesPerSecond = thresholdKb * 1024f
            return txBytesPerSecond > thresholdBytesPerSecond ||
                    rxBytesPerSecond > thresholdBytesPerSecond
        }

        fun calculateSpeedString(
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

        try {
            val handlerClass = param.classLoader.loadClass($$"$${controllerString}$NetworkSpeedManager$1")
            val handleMessageMethod = handlerClass.getDeclaredMethod("handleMessage", Message::class.java)
            xposedModule.hook(handleMessageMethod).intercept { chain ->
                try {
                    val message = chain.args[0] as Message
                    val handler = chain.thisObject as Handler
                    val observable = chain.thisObject.reflect["this$0"] as? java.util.Observable
                    if (observable != null && observable.countObservers() > 0) {
                        when (message.what) {
                            messageInitial -> {
                                lastNetworkStats = getCurrentNetworkStats()
                                lastUpdateTime = SystemClock.elapsedRealtime()
                                handler.scheduleNextUpdate()
                            }

                            messageUpdate -> {
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

                                    observable.reflect.call("setChanged")
                                    observable.notifyObservers(speedString)
                                }

                                lastNetworkStats = currentNetworkStats
                                lastUpdateTime = currentTime
                                handler.scheduleNextUpdate()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                null
            }

            val netspeedViewClass = param.classLoader.loadClass(netSpeedViewString)
            val onFinishInflateMethod = netspeedViewClass.getDeclaredMethod("onFinishInflate")
            xposedModule.hook(onFinishInflateMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    (chain.thisObject.reflect["mContentView"] as? TextView)?.apply {
                        setLines(2)
                        gravity = Gravity.END
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
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

    @SuppressLint("PrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun turnOn5gQsTile() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        if (param.packageName == Package.SYSTEMUI ||
            param.packageName == Package.TELEPHONYUI
        ) {
            try {
                val semCscFeatureClass =
                    param.classLoader.loadClass("com.samsung.android.feature.SemCscFeature")
                semCscFeatureClass.declaredMethods
                    .filter { it.name == "getString" }
                    .forEach { method ->
                        xposedModule.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (chain.args.firstOrNull() == "CscFeature_SystemUI_ConfigDefQuickSettingItem") {
                                val quickSettingItem = result as? String ?: ""
                                if (!quickSettingItem.contains("TurnOn5g")) {
                                    "$quickSettingItem,TurnOn5g"
                                } else {
                                    quickSettingItem
                                }
                            } else {
                                result
                            }
                        }
                    }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
        if (param.packageName == Package.SYSTEMUI) {
            try {
                val qsTileHostClass = param.classLoader.loadClass("com.android.systemui.qs.QSTileHost")
                val isAvailableCustomTileMethod =
                    qsTileHostClass.getDeclaredMethod("isAvailableCustomTile", String::class.java)
                xposedModule.hook(isAvailableCustomTileMethod).intercept { chain ->
                    if (chain.args.firstOrNull() == "TurnOn5g") {
                        true
                    } else {
                        chain.proceed()
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showWiFiLinkSpeed() {
        if (param.packageName != Package.SETTINGS) {
            return
        }
        try {
            val connectedListAdapterClass = param.classLoader.loadClass(
                "com.samsung.android.settings.wifi.ConnectedListAdapter"
            )
            val viewHolderClass = param.classLoader.loadClass(
                $$"androidx.recyclerview.widget.RecyclerView$ViewHolder"
            )

            fun getLinkSpeed(thisObject: Any, position: Int): String? {
                val wifiEntries = thisObject.reflect["mWifiEntries"] as? List<*> ?: return null
                val wifiEntry = wifiEntries.getOrNull(position) ?: return null
                val wifiInfo = wifiEntry.reflect["mWifiInfo"] as? WifiInfo ?: return null
                return "${wifiInfo.txLinkSpeedMbps},${wifiInfo.rxLinkSpeedMbps}"
            }

            val onBindViewHolderMethod = connectedListAdapterClass.getDeclaredMethod(
                "onBindViewHolder",
                viewHolderClass,
                Int::class.javaPrimitiveType
            )
            xposedModule.hook(onBindViewHolderMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val position = chain.args[1] as Int
                    val linkSpeed = getLinkSpeed(chain.thisObject, position)
                    if (linkSpeed != null) {
                        val mSummary = chain.args[0].reflect["mSummary"] as? TextView
                        mSummary?.append(" $linkSpeed")
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
}
