package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ScaleXSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.NetworkSpeedOption
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
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
    fun showCustomNetworkSpeed(
        statusBar: Preference.SystemUI.StatusBar,
        intervalMillisecond: Long = 3000L,
    ) = afterAttach {
        if (param.packageName != Package.SYSTEMUI || intervalMillisecond <= 0L) {
            return@afterAttach
        }
        val layout = NetworkSpeedOption.Layout.entries.getOrElse(statusBar.networkSpeedLayout) {
            NetworkSpeedOption.Layout.SYSTEM
        }
        val netSpeedViewString = "${Package.SYSTEMUI}.statusbar.policy.NetspeedView"
        val controllerString = "${netSpeedViewString}Controller"

        data class NetworkStats(val totalTx: Long, val totalRx: Long, val interfaces: Set<String>)

        val messageInitial = 1
        val messageUpdate = 2

        var speedView: TextView? = null
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
                val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                while (networkInterfaces.hasMoreElements()) {
                    val networkInterface = networkInterfaces.nextElement()
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
                xlog(t)
            }
            return NetworkStats(totalTx, totalRx, validInterfaces)
        }

        val markerUp = if (statusBar.showNetworkSpeedArrows) NetworkSpeedOption.MARKERS_UP.getOrElse(statusBar.networkSpeedMarker) { "↑" } else ""
        val markerDown = if (statusBar.showNetworkSpeedArrows) NetworkSpeedOption.MARKERS_DOWN.getOrElse(statusBar.networkSpeedMarker) { "↓" } else ""
        val unit = NetworkSpeedOption.UNITS.getOrElse(statusBar.networkSpeedUnit) { NetworkSpeedOption.UNITS[0] }
        val threshold = statusBar.networkSpeedThreshold
        val textSizeSp = statusBar.networkSpeedTextSizeSp
        val markerSizeSp = statusBar.networkSpeedMarkerSizeSp
        val markerGap = statusBar.networkSpeedMarkerGap
        val lineSpacingDp = statusBar.networkSpeedLineSpacingDp

        fun formatSpeed(bytesPerSecond: Float): String {
            val scaled = if (unit.bits) bytesPerSecond * 8f else bytesPerSecond
            if (scaled <= 0f) return "0${unit.byteSuffix}"
            if (scaled < unit.base) return "${scaled.roundToInt()}${unit.byteSuffix}"
            val kilos = scaled / unit.base
            if (kilos < 100f) return "%.2f${unit.kiloPrefix}".format(kilos)
            if (kilos < 1000f) return "%.1f${unit.kiloPrefix}".format(kilos)
            val megas = kilos / unit.base
            return if (megas < 100f) "%.2f${unit.megaPrefix}".format(megas) else "%.1f${unit.megaPrefix}".format(megas)
        }

        fun shouldDisplay(tx: Float, rx: Float): Boolean {
            if (threshold <= 0) return true
            val thresholdBytes = threshold.toFloat() * unit.base / (if (unit.bits) 8f else 1f)
            return tx > thresholdBytes || rx > thresholdBytes
        }

        fun appendReading(text: SpannableStringBuilder, marker: String, speed: String, extraGap: Float = 0f) {
            if (marker.isNotEmpty()) {
                val start = text.length
                text.append(marker)
                if (markerSizeSp > 0f) {
                    text.setSpan(AbsoluteSizeSpan(markerSizeSp.roundToInt(), true), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            val gap = (if (marker.isNotEmpty()) markerGap else 0f) + extraGap
            if (gap > 0f) {
                val gapStart = text.length
                text.append(" ")
                if (gap != 1f) {
                    text.setSpan(ScaleXSpan(gap), gapStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            text.append(speed)
        }

        fun calculateSpeedString(txBytesPerSec: Float, rxBytesPerSec: Float): CharSequence {
            if (!shouldDisplay(txBytesPerSec, rxBytesPerSec)) return ""
            val result = SpannableStringBuilder()
            when (layout) {
                NetworkSpeedOption.Layout.ACTIVE_DIRECTION -> {
                    val isTx = txBytesPerSec >= rxBytesPerSec
                    val speed = maxOf(txBytesPerSec, rxBytesPerSec)
                    if (speed < 1f) return ""
                    appendReading(result, if (isTx) markerUp else markerDown, formatSpeed(speed))
                }
                NetworkSpeedOption.Layout.SPLIT_HORIZONTAL -> {
                    appendReading(result, markerUp, formatSpeed(txBytesPerSec))
                    result.append(' ')
                    appendReading(result, markerDown, formatSpeed(rxBytesPerSec))
                }
                else -> { // Layout.SPLIT_VERTICAL
                    val speedUp = formatSpeed(txBytesPerSec)
                    val speedDown = formatSpeed(rxBytesPerSec)
                    val paint = speedView?.paint
                    val wUp = paint?.measureText(speedUp) ?: 0f
                    val wDown = paint?.measureText(speedDown) ?: 0f
                    val spaceW = paint?.measureText(" ")?.takeIf { it > 0f } ?: 1f
                    appendReading(result, markerUp, speedUp, if (wDown > wUp) (wDown - wUp) / spaceW else 0f)
                    result.append('\u00A0').append('\n')
                    appendReading(result, markerDown, speedDown, if (wUp > wDown) (wUp - wDown) / spaceW else 0f)
                }
            }
            return result.append('\u00A0')
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
                                    val tx = (currentNetworkStats.totalTx - lastNetworkStats.totalTx) / actualIntervalSeconds
                                    val rx = (currentNetworkStats.totalRx - lastNetworkStats.totalRx) / actualIntervalSeconds
                                    val speedString = calculateSpeedString(tx, rx)

                                    observable.reflect.call("setChanged")
                                    try {
                                        observable.notifyObservers(speedString.toString())
                                    } catch (t: Throwable) {
                                        xlog(t)
                                    }
                                    if (speedString.isNotEmpty()) {
                                        speedView?.apply {
                                            visibility = View.VISIBLE
                                            text = speedString
                                        }
                                    }
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
                        speedView = this
                        setLines(if (layout == NetworkSpeedOption.Layout.SPLIT_VERTICAL) 2 else 1)
                        gravity = Gravity.START
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        if (textSizeSp > 0f) setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                        if (lineSpacingDp > 0f) {
                            setLineSpacing(
                                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, lineSpacingDp, resources.displayMetrics),
                                1f
                            )
                        }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }

            if (textSizeSp > 0f) {
                val scaleViewMethod = netspeedViewClass.getDeclaredMethod("scaleView", Float::class.javaPrimitiveType)
                xposedModule.hook(scaleViewMethod).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        speedView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                    result
                }
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
