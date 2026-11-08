package io.github.soclear.oneuix.hook.systemui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import kotlin.math.roundToInt

object StatusBarSpacing {
    /** The two physical gaps measured on Samsung's LTR main status bar. */
    context(xposedModule: XposedModule)
    fun setNativeGaps(iconPaddingDp: Float?, clockMarginDp: Float?) =
        afterAttachTry(iconPaddingDp != null || clockMarginDp != null) {
            val controller = classLoader.loadClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarViewController"
            )
            xposedModule.hook(controller.getDeclaredMethod("onViewAttached")).intercept { chain ->
                val result = chain.proceed()
                try {
                    val bar = chain.thisObject.reflect["mView"] as? View
                    if (bar != null) watch(bar, iconPaddingDp, clockMarginDp)
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        }

    context(xposedModule: XposedModule)
    private fun watch(bar: View, iconPaddingDp: Float?, clockMarginDp: Float?) {
        val iconsId = bar.resources.getIdentifier("statusIcons", "id", Package.SYSTEMUI)
        val clockId = bar.resources.getIdentifier("right_clock_container", "id", Package.SYSTEMUI)
        var failed = false
        fun apply() {
            if (failed || bar.layoutDirection != View.LAYOUT_DIRECTION_LTR) return
            try {
                val density = bar.resources.displayMetrics.density
                val iconPadding = iconPaddingDp?.let { (it * density).roundToInt().coerceAtLeast(0) }
                val clockMargin = clockMarginDp?.let { (it * density).roundToInt().coerceAtLeast(0) }
                if (iconPadding != null && iconsId != 0) {
                    bar.findViewById<View>(iconsId)?.let { icons ->
                        if (icons.paddingRight != iconPadding) {
                            icons.setPadding(icons.paddingLeft, icons.paddingTop, iconPadding, icons.paddingBottom)
                        }
                    }
                }
                if (clockMargin != null && clockId != 0) {
                    bar.findViewById<View>(clockId)?.let { clock ->
                        val params = clock.layoutParams as? ViewGroup.MarginLayoutParams
                        if (params != null && (params.leftMargin != clockMargin ||
                                (params.isMarginRelative && params.marginStart != clockMargin))) {
                            params.leftMargin = clockMargin
                            if (params.isMarginRelative) params.marginStart = clockMargin
                            clock.layoutParams = params
                        }
                    }
                }
            } catch (t: Throwable) {
                failed = true
                xlog(t)
            }
        }

        // Reapply after Samsung refreshes layout/resources, including a fold or clock move.
        // Only changed values request another layout, so a settled bar does no layout work.
        val observer = bar.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener { apply() }
        observer.addOnGlobalLayoutListener(listener)
        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                view.removeOnAttachStateChangeListener(this)
            }
        })
        apply()
    }
}
