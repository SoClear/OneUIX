package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import kotlin.math.roundToInt
import androidx.core.view.isNotEmpty


@SuppressLint("PrivateApi")
object Launcher {
    context(xposedModule: XposedModule)
    fun setDockBottomPadding(paddingDp: Float) = afterAttachTry {
        val clazz = classLoader.loadClass(
            "com.honeyspace.ui.honeypots.hotseat.presentation.HotseatCellLayout"
        )
        xposedModule.hook(clazz.declaredMethods.single { it.name == "onLayout" }).intercept { chain ->
            val result = chain.proceed()
            val layout = chain.thisObject as ViewGroup
            if (layout.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                layout.resources.configuration.smallestScreenWidthDp < 600 && layout.isNotEmpty()
            ) {
                val icon = layout.getChildAt(0)
                val style = icon.reflect.call("getItemStyle") ?: return@intercept result
                val position = style.reflect.call("getPosition") as Point
                val balloon = layout.parent as View
                val dock = balloon.parent as View
                val bottom = balloon.top + layout.top + icon.top + position.y +
                    (style.reflect.call("getItemSize") as Int)
                val padding = (paddingDp * layout.resources.displayMetrics.density).roundToInt()
                // Move the real dock bounds, not just padding or the drawn icons.
                val params = dock.layoutParams as ViewGroup.MarginLayoutParams
                val top = dock.rootView.height - padding - bottom
                if (params.topMargin != top) {
                    params.topMargin = top
                    dock.layoutParams = params
                }
                dock.setPadding(dock.paddingLeft, dock.paddingTop, dock.paddingRight, 0)
            }
            result
        }
    }

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
            return "%.2fG %d%% %.2fG %d%%".format(
                freeGB,
                freePercentage,
                usedGB,
                usedPercentage
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

    @Serializable
    private data class LauncherHookConfig(
        override val versionCode: Long,
        val gridItemDecorationClass: String,
        val stylerFieldName: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): LauncherHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val decorClassData = bridge.findClass {
                excludePackages(listOf("android", "androidx", "com", "kotlin", "kotlinx"))
                matcher {
                    methods {
                        add { name = "getItemOffsets" }
                    }
                    fields {
                        count(2..20)
                        add { type("com.honeyspace.common.recentstyler.RecentStylerV2") }
                    }
                }
            }.singleOrNull() ?: return null

            val stylerField = decorClassData.findField {
                matcher {
                    type("com.honeyspace.common.recentstyler.RecentStylerV2")
                }
            }.singleOrNull() ?: return null

            return LauncherHookConfig(
                versionCode = longVersionCode,
                gridItemDecorationClass = decorClassData.name,
                stylerFieldName = stylerField.name,
            )
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun enableThreeRowsRecentsGrid() {
        if (param.packageName != Package.LAUNCHER) return

        afterAttach {
            val hookConfig = getHookConfig {
                getHookConfigFromDexKit()
            } ?: return@afterAttach

            try {
                val glmClass = classLoader.loadClass(
                    "com.honeyspace.ui.honeypots.tasklist.presentation.layoutmanager.RecentsGridLayoutManager"
                )
                val glmBase = classLoader.loadClass("androidx.recyclerview.widget.GridLayoutManager")
                val rvClass = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView")
                val recyclerClass = classLoader.loadClass($$"androidx.recyclerview.widget.RecyclerView$Recycler")
                val stateClass = classLoader.loadClass($$"androidx.recyclerview.widget.RecyclerView$State")
                val lmBaseClass = classLoader.loadClass($$"androidx.recyclerview.widget.RecyclerView$LayoutManager")
                val decorClass = classLoader.loadClass(hookConfig.gridItemDecorationClass)
                val stylerFieldName = hookConfig.stylerFieldName

                fun isPortrait(v: View?) =
                    v?.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT

                fun updateSpan(lm: Any?, view: View?) {
                    if (lm != null && glmClass.isInstance(lm)) {
                        val target = if (isPortrait(view)) 3 else 2
                        if (lm.reflect.call("getSpanCount") != target) lm.reflect.call("setSpanCount", target)
                    }
                }

                // 1. 边距与位置对齐（拦截动态解析出的 ItemDecoration getItemOffsets）
                decorClass.declaredMethods
                    .filter { it.name == "getItemOffsets" && it.parameterTypes.firstOrNull() == Rect::class.java }
                    .forEach { method ->
                        xposedModule.hook(method).intercept { chain ->
                            try {
                                val outRect = chain.args[0] as? Rect ?: return@intercept chain.proceed()
                                val view = chain.args.getOrNull(1) as? View
                                val parent = chain.args.getOrNull(2) as? View ?: return@intercept chain.proceed()
                                if (!isPortrait(parent)) return@intercept chain.proceed()

                                val lm = (parent as? ViewGroup)?.reflect?.call("getLayoutManager")
                                if (lm?.reflect?.call("getSpanCount") != 3 || !glmClass.isInstance(lm)) {
                                    return@intercept chain.proceed()
                                }

                                val styler = chain.thisObject.reflect[stylerFieldName]
                                val styleData =
                                    styler?.reflect?.call("getStyleData") ?: return@intercept chain.proceed()

                                val actualPos = (chain.args.getOrNull(1) as? Int)
                                    ?: (view?.let { lm.reflect.call("getPosition", it) as? Int }
                                        ?: -1).takeIf { it != -1 }
                                    ?: (view?.let { parent.reflect.call("getChildAdapterPosition", it) as? Int } ?: 0)

                                val row = actualPos % 3
                                val col = actualPos / 3
                                val cardHeight = (styleData.reflect.call("getTaskViewCoordinate") as RectF).height()
                                val pageSpacing = (styleData.reflect.call("getPageSpacing") as Number).toInt()
                                val pageSideMargin = (styleData.reflect.call("getPageSideMargin") as Number).toInt()

                                val topInset = parent.rootWindowInsets?.getInsetsIgnoringVisibility(
                                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                                )?.top?.toFloat() ?: 128f
                                val targetTop = (topInset + 6f).coerceAtLeast(134f)

                                val decor = parent.rootView as? ViewGroup
                                val memHeight =
                                    (0 until (decor?.childCount
                                        ?: 0)).firstNotNullOfOrNull { decor?.getChildAt(it) as? TextView }?.height?.toFloat()
                                        ?: 0f
                                val bottomReserved = if (memHeight > 0) memHeight + 15f else 138f
                                val gap = ((parent.height - bottomReserved - targetTop - 3 * cardHeight) / 2)
                                    .coerceIn(0f, (styleData.reflect.call("getRowGap") as Number).toFloat())

                                val borders = lm.reflect["mCachedBorders"] as? IntArray
                                val laneStart = borders?.getOrNull(row) ?: (row * parent.height / 3)
                                val topOffset = (targetTop + row * (cardHeight + gap) - laneStart).roundToInt()

                                val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
                                val side = if (col == 0) pageSideMargin else pageSpacing / 2
                                val halfGap = pageSpacing / 2

                                outRect.set(if (isRtl) side else halfGap, topOffset, if (isRtl) halfGap else side, 0)
                                null
                            } catch (_: Throwable) {
                                chain.proceed()
                            }
                        }
                    }

                // 2. 绑定 LayoutManager 与布局时动态同步 spanCount
                xposedModule.hook(rvClass.getDeclaredMethod("setLayoutManager", lmBaseClass)).intercept { chain ->
                    chain.proceed().also { updateSpan(chain.args[0], chain.thisObject as? View) }
                }

                xposedModule.hook(glmBase.getDeclaredMethod("onLayoutChildren", recyclerClass, stateClass))
                    .intercept { chain ->
                        val lm = chain.thisObject
                        val rv = lm?.reflect?.get("mRecyclerView") as? View
                        updateSpan(lm, rv)
                        if (isPortrait(rv)) runCatching { rv?.reflect?.call("markItemDecorInsetsDirty") }
                        chain.proceed()
                    }

                // 3. 划掉卡片或列表项变动时通知刷新 ItemDecoration
                glmBase.declaredMethods
                    .filter {
                        it.name in setOf("onItemsRemoved", "onItemsAdded", "onItemsMoved", "onItemsChanged") &&
                                it.parameterTypes.firstOrNull() == rvClass
                    }
                    .forEach { method ->
                        xposedModule.hook(method).intercept { chain ->
                            chain.proceed().also {
                                val rv = chain.args[0] as? View
                                if (isPortrait(rv)) {
                                    runCatching { rv?.reflect?.call("markItemDecorInsetsDirty") }
                                    rv?.post { runCatching { rv.reflect.call("invalidateItemDecorations") } }
                                }
                            }
                        }
                    }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }
}
