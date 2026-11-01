package io.github.soclear.oneuix.hook.systemui.statusbar

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import android.content.om.OverlayInfo
import android.content.om.OverlayManager
import android.os.Build
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

@SuppressLint("PrivateApi", "DiscouragedApi")
object DualStatusBar {
    private class Location(view: View, contents: ViewGroup) {
        val parent = WeakReference(view.parent as ViewGroup)
        val index = (view.parent as ViewGroup).indexOfChild(view)
        val params = view.layoutParams
        val children = (0 until contents.childCount).associate { index ->
            val child = contents.getChildAt(index)
            child.id to RelativeLayout.LayoutParams(child.layoutParams as RelativeLayout.LayoutParams)
        }
    }

    private fun Resources.rowHeight() = (24 * displayMetrics.density).roundToInt()

    context(xposedModule: XposedModule)
    fun apply() = afterAttachTry {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@afterAttachTry
        // Do not split an ordinary-height bar when the resource update failed or was not applied.
        val overlays = getSystemService(OverlayManager::class.java).reflect.call(
            "getOverlayInfosForTarget", "android", Process.myUserHandle(),
        ) as List<*>
        val name = BuildConfig.MODULE_APPLICATION_ID.replace('.', '_') + "_DualStatusBar"
        if (overlays.none { it is OverlayInfo && it.overlayName == name && it.reflect.call("isEnabled") == true }) {
            return@afterAttachTry
        }
        val gardener = classLoader.loadClass("com.android.systemui.statusbar.phone.IndicatorBasicGardener")
        val update = gardener.declaredMethods.single { it.name == "updateGarden" }
        val locations = WeakHashMap<ViewGroup, Location>()

        xposedModule.hook(update).intercept { chain ->
            val result = chain.proceed()
            try {
                if (chain.thisObject.reflect["gardenName"] == "PhoneStatusBarViewController") {
                    val bar = chain.thisObject.reflect["gardenView"]!!.reflect
                        .call("getHeightContainer") as ViewGroup
                    fun find(name: String): View? = bar.findViewById(
                        bar.resources.getIdentifier(name, "id", Package.SYSTEMUI)
                    )
                    val contents = find("status_bar_contents") as? RelativeLayout
                    val notifications = find("samsung_notification_indicator_container")
                    if (contents != null && notifications != null) {
                        val portrait = bar.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                        if (portrait) {
                            val rowHeight = bar.resources.rowHeight()
                            val params = contents.layoutParams as ViewGroup.MarginLayoutParams
                            val original = locations.getOrPut(bar) { Location(notifications, contents) }
                            val height = params.height - contents.paddingTop - contents.paddingBottom - rowHeight
                            original.children.keys.forEach { id ->
                                val child = contents.findViewById<View>(id)
                                val childParams = child.layoutParams as RelativeLayout.LayoutParams
                                if (childParams.height != height) {
                                    childParams.height = height
                                    childParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                                    child.layoutParams = childParams
                                }
                            }
                            if (notifications.parent != contents) {
                                (notifications.parent as ViewGroup).removeView(notifications)
                                contents.addView(notifications, RelativeLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, rowHeight,
                                ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) })
                            } else if (notifications.layoutParams.height != rowHeight) {
                                notifications.layoutParams = notifications.layoutParams.apply { this.height = rowHeight }
                            }
                        } else {
                            locations.remove(bar)?.let { original ->
                                original.parent.get()?.let { parent ->
                                    contents.removeView(notifications)
                                    parent.addView(notifications, original.index, original.params)
                                }
                                original.children.forEach { (id, params) ->
                                    contents.findViewById<View>(id).layoutParams = params
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
            result
        }
    }
}
