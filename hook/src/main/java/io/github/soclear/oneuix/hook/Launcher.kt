package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import kotlin.math.roundToInt


@SuppressLint("PrivateApi")
object Launcher {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showMemoryUsageInRecents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            showMemoryUsageInRecentsTargetSdk36()
        } else {
            showMemoryUsageInRecentsTargetSdk35()
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showMemoryUsageInRecentsTargetSdk36() {
        if (param.packageName != Package.LAUNCHER ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA
        ) return

        var memoryTextView: TextView? = null
        var activityManager: ActivityManager? = null
        var activityRef: WeakReference<Activity>? = null

        fun dpToPx(dp: Int, view: View): Int {
            return (dp * view.resources.displayMetrics.density).roundToInt()
        }

        try {
            xposedModule.hook(
                param.classLoader.loadClass("com.android.quickstep.RecentsActivity")
                    .getDeclaredMethod("onCreate", Bundle::class.java)
            ).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as Activity
                if (activityManager == null) {
                    activityManager = activity.getSystemService(ActivityManager::class.java)
                }
                (memoryTextView?.parent as? ViewGroup)?.removeView(memoryTextView)
                val decorView = activity.window.decorView as? FrameLayout ?: return@intercept result
                memoryTextView = TextView(activity).apply {
                    setTextColor(Color.WHITE)
                    typeface = Typeface.MONOSPACE
                    setShadowLayer(5f, 0f, 0f, Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        bottomMargin = dpToPx(0, decorView)
                    }
                }
                decorView.addView(memoryTextView)
                activityRef = WeakReference(activity)
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }


        var lastUpdateTime = 0L
        val memoryInfo = ActivityManager.MemoryInfo()
        val swapTotal by lazy {
            File("/proc/meminfo").readLines()
                .first { it.startsWith("SwapTotal:") }
                .split(Regex("\\s+"))[1].toLong() * 1024
        }

        fun formatMemory(freeBytes: Long, totalBytes: Long): String {
            val freeBytesDouble = freeBytes.toDouble()
            val totalBytesDouble = totalBytes.toDouble()
            val divisor = 1024 * 1024 * 1024
            val freeGB = freeBytesDouble / divisor
            val freePercentage = (freeBytesDouble / totalBytes * 100).roundToInt()
            val usedGB = (totalBytesDouble - freeBytesDouble) / divisor
            val usedPercentage = 100 - freePercentage
            val totalGB = totalBytesDouble / divisor
            return "%.2fG %d%% %.2fG %d%% %.2fG".format(
                freeGB,
                freePercentage,
                usedGB,
                usedPercentage,
                totalGB
            )
        }

        fun getMemoryText(): String {
            activityManager?.getMemoryInfo(memoryInfo) ?: return ""
            val leftText = formatMemory(memoryInfo.availMem, memoryInfo.totalMem)
            val swapFree = File("/proc/meminfo").readLines()
                .first { it.startsWith("SwapFree:") }
                .split(Regex("\\s+"))[1].toLong() * 1024
            val rightText = formatMemory(swapFree, swapTotal)
            return "$leftText   $rightText"
        }

        fun updateMemoryText() {
            val currentTime = System.nanoTime()
            if (currentTime - lastUpdateTime < 300_000_000L) {
                return
            }
            lastUpdateTime = currentTime

            val memoryText = getMemoryText()
            activityRef?.get()?.runOnUiThread {
                memoryTextView?.text = memoryText
            }
        }

        try {
            xposedModule.hook(
                param.classLoader.loadClass("com.android.quickstep.RecentsActivity")
                    .getDeclaredMethod("onResume")
            ).intercept { chain ->
                val result = chain.proceed()
                updateMemoryText()
                result
            }

            xposedModule.hook(
                param.classLoader.loadClass($$"com.android.systemui.shared.system.TaskStackChangeListeners$Impl")
                    .getDeclaredMethod("onTaskRemoved", Int::class.javaPrimitiveType)
            ).intercept { chain ->
                val result = chain.proceed()
                updateMemoryText()
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    @SuppressLint("ResourceType")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showMemoryUsageInRecentsTargetSdk35() {
        if (param.packageName != Package.LAUNCHER ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return

        var leftTextView: TextView? = null
        var rightTextView: TextView? = null
        var activityRef: WeakReference<Activity>? = null
        var activityManager: ActivityManager? = null

        fun addTextView(
            textView: TextView,
            isLeft: Boolean,
            clearAllButton: Button,
            constraintLayout: ViewGroup,
            constraintSetConstructor: Constructor<*>,
            constraintLayoutLayoutParamsConstructor: Constructor<*>,
        ) {
            val initialParams = constraintLayoutLayoutParamsConstructor.newInstance(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            constraintLayout.reflect.call("addView", textView, initialParams)

            val constraintSet = constraintSetConstructor.newInstance()
            constraintSet.reflect.call("clone", constraintLayout)

            val top = 3
            val bottom = 4
            val start = 6
            val end = 7
            val parentId = 0
            // 通用垂直约束：顶部和底部与按钮对齐，实现垂直居中
            constraintSet.reflect.call("connect", textView.id, top, clearAllButton.id, top)
            constraintSet.reflect.call("connect", textView.id, bottom, clearAllButton.id, bottom)
            if (isLeft) {
                // 左侧 TextView
                constraintSet.reflect.call("connect", textView.id, end, clearAllButton.id, start)
                constraintSet.reflect.call("connect", textView.id, start, parentId, start)
            } else {
                // 右侧 TextView
                constraintSet.reflect.call("connect", textView.id, start, clearAllButton.id, end)
                constraintSet.reflect.call("connect", textView.id, end, parentId, end)
            }

            // 应用约束
            constraintSet.reflect.call("applyTo", constraintLayout)
        }

        try {
            val constraintSetConstructor = param.classLoader.loadClass(
                "androidx.constraintlayout.widget.ConstraintSet"
            ).getConstructor()
            val constraintLayoutLayoutParamsConstructor = param.classLoader.loadClass(
                $$"androidx.constraintlayout.widget.ConstraintLayout$LayoutParams"
            ).getConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )

            xposedModule.hook(
                param.classLoader.loadClass("com.honeyspace.common.entity.HoneyPot")
                    .getDeclaredMethod("getView")
            ).intercept { chain ->
                val result = chain.proceed()
                try {
                    if (chain.thisObject.reflect.call("getTAG") != "TaskListPot") return@intercept result
                    leftTextView?.let { (it.parent as ViewGroup).removeView(it) }
                    rightTextView?.let { (it.parent as ViewGroup).removeView(it) }
                    val view = result as View
                    val buttonId = view.resources.getIdentifier("clear_all", "id", Package.LAUNCHER)
                    val clearAllButton: Button = view.findViewById(buttonId)
                    val constraintLayout = clearAllButton.parent as ViewGroup

                    leftTextView = TextView(constraintLayout.context).apply {
                        id = View.generateViewId()
                        setTextColor(Color.WHITE)
                        setTypeface(Typeface.MONOSPACE)
                        fontFeatureSettings = "'tnum' 1, 'pnum' 0"

                    }

                    rightTextView = TextView(constraintLayout.context).apply {
                        id = View.generateViewId()
                        setTextColor(Color.WHITE)
                        setTypeface(Typeface.MONOSPACE)
                        fontFeatureSettings = "'tnum' 1, 'pnum' 0"
                    }

                    addTextView(
                        leftTextView,
                        true,
                        clearAllButton,
                        constraintLayout,
                        constraintSetConstructor,
                        constraintLayoutLayoutParamsConstructor
                    )

                    addTextView(
                        rightTextView,
                        false,
                        clearAllButton,
                        constraintLayout,
                        constraintSetConstructor,
                        constraintLayoutLayoutParamsConstructor
                    )
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }

            xposedModule.hook(
                param.classLoader.loadClass("com.android.quickstep.RecentsActivity")
                    .getDeclaredMethod("onCreate", Bundle::class.java)
            ).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as Activity
                activityRef = WeakReference(activity)
                activityManager = activity.getSystemService(ActivityManager::class.java)
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }


        var lastUpdateTime = 0L
        val memoryInfo = ActivityManager.MemoryInfo()
        val swapTotal by lazy {
            File("/proc/meminfo").readLines()
                .first { it.startsWith("SwapTotal:") }
                .split(Regex("\\s+"))[1].toLong() * 1024
        }

        fun formatMemory(freeBytes: Long, totalBytes: Long): String {
            val freeBytesDouble = freeBytes.toDouble()
            val totalBytesDouble = totalBytes.toDouble()
            val divisor = 1024 * 1024 * 1024
            val freeGB = freeBytesDouble / divisor
            val freePercentage = (freeBytesDouble / totalBytes * 100).roundToInt()
            val usedGB = (totalBytesDouble - freeBytesDouble) / divisor
            val usedPercentage = 100 - freePercentage
            val totalGB = totalBytesDouble / divisor
            return """
                %5.2fGB%3d%%
                %5.2fGB%3d%%
                %5.2fGB
            """.trimIndent().format(
                freeGB, freePercentage,
                usedGB, usedPercentage,
                totalGB
            ).trimIndent()
        }

        fun getMemoryText(): Pair<String, String> {
            activityManager?.getMemoryInfo(memoryInfo) ?: return "" to ""
            val leftText = formatMemory(memoryInfo.availMem, memoryInfo.totalMem)
            val swapFree = File("/proc/meminfo").readLines()
                .first { it.startsWith("SwapFree:") }
                .split(Regex("\\s+"))[1].toLong() * 1024
            val rightText = formatMemory(swapFree, swapTotal)
            return leftText to rightText
        }

        fun updateMemoryText() {
            val currentTime = System.nanoTime()
            if (currentTime - lastUpdateTime < 300_000_000L) {
                return
            }
            lastUpdateTime = currentTime

            val memoryText = getMemoryText()
            activityRef?.get()?.runOnUiThread {
                leftTextView?.text = memoryText.first
                rightTextView?.text = memoryText.second
            }
        }

        try {
            xposedModule.hook(
                param.classLoader.loadClass("com.android.quickstep.RecentsActivity")
                    .getDeclaredMethod("onResume")
            ).intercept { chain ->
                val result = chain.proceed()
                updateMemoryText()
                result
            }

            xposedModule.hook(
                param.classLoader.loadClass($$"com.android.systemui.shared.system.TaskStackChangeListeners$Impl")
                    .getDeclaredMethod("onTaskRemoved", Int::class.javaPrimitiveType)
            ).intercept { chain ->
                val result = chain.proceed()
                updateMemoryText()
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun removeShortcutBadge() {
        if (param.packageName != Package.LAUNCHER) return
        try {
            // 阻止工作资料（ WORK_APP ）/安全文件夹（ SECURE_FOLDER ）/即时应用（ INSTANT_APP ）/中国可卸载应用（ CHINA_REMOVABLE ）等角标
            val componentKeyType =
                param.classLoader.loadClass("com.honeyspace.sdk.source.entity.ComponentKey")
            xposedModule.hook(
                param.classLoader.loadClass("com.honeyspace.ui.common.iconview.AppShortcutBadgeCreator")
                    .getDeclaredMethod("create", Context::class.java, componentKeyType)
            ).intercept { null }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            // 阻止深度快捷方式右下角的小图标
            xposedModule.hook(
                param.classLoader.loadClass("com.honeyspace.ui.common.iconview.DeepShortcutIconSupplier")
                    .getDeclaredMethod(
                        "drawSmallIcon",
                        android.graphics.Canvas::class.java,
                        android.content.pm.ShortcutInfo::class.java,
                        Boolean::class.javaPrimitiveType
                    )
            ).intercept { null }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideRecentsCloseAllButton() {
        try {
            xposedModule.hook(
                param.classLoader.loadClass("com.honeyspace.ui.honeypots.tasklist.presentation.CloseAllButton")
                    .getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
            ).intercept { chain ->
                val result = chain.proceed()
                // The launcher also fires "Close all" by hit-testing the button's
                // global visible rect during the recents gesture, independently of its
                // click listener. GONE stops clicks, but a GONE view keeps its last
                // laid-out bounds, leaving a phantom hit area; collapsing the width to
                // zero clears that rect so neither path can trigger.
                val button = chain.thisObject as View
                val hide = {
                    button.visibility = View.GONE
                    button.right = button.left
                }
                hide()
                button.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> hide() }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideAppsSearchBar() {
        try {
            xposedModule.hook(
                param.classLoader.loadClass("com.honeyspace.ui.honeypots.appscreen.presentation.AppsSearchBar")
                    .getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
            ).intercept { chain ->
                val result = chain.proceed()
                val searchBar = chain.thisObject as View
                searchBar.addOnAttachStateChangeListener(object :
                    View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.visibility = View.GONE
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
                // 监听该 View 自身布局变化，防止 Data Binding 将 visibility 重置为 VISIBLE
                searchBar.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    if (view.visibility != View.GONE) {
                        view.visibility = View.GONE
                    }
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
