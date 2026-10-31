package io.github.soclear.oneuix.hook.systemui.statusbar

import android.annotation.SuppressLint
import android.os.Message
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import java.lang.reflect.Field
import java.util.Observable
import io.github.soclear.oneuix.common.Preference.SystemUI.StatusBar.NetWorkSpeed as Config

@Suppress("DEPRECATION")
@SuppressLint("PrivateApi")
object NetWorkSpeed {
    context(xposedModule: XposedModule)
    fun supportRealTime() = afterAttachTry {
        val observer = classLoader.loadClass(
            $$"com.android.systemui.statusbar.policy.NetspeedViewController$SettingObserver"
        )
        xposedModule.hook(observer.getDeclaredMethod("updateSettingValues")).intercept { chain ->
            val result = chain.proceed()
            chain.thisObject.reflect["mIsOnSecSettings"] = true
            result
        }
    }

    context(xposedModule: XposedModule)
    fun configure(config: Config) = afterAttachTry {
        System.loadLibrary("oneuix_network")
        val view = classLoader.loadClass("com.android.systemui.statusbar.policy.NetspeedView")
        val handler = classLoader.loadClass(
            $$"com.android.systemui.statusbar.policy.NetspeedViewController$NetworkSpeedManager$1"
        )
        initialize(
            view.getDeclaredField("mContentView"),
            view.getDeclaredField("mContentUpdated"),
            handler.getDeclaredField($$"this$0"),
            config.layout.ordinal,
            if (config.showArrows) config.marker.up else "",
            if (config.showArrows) config.marker.down else "",
            config.unit.bits, config.unit.base, config.showPerSecond,
            config.textSizeSp, config.markerSizeSp, config.markerGap,
            config.lineSpacingDp, config.threshold,
            config.refreshInterval.milliseconds, config.paddingEndDp,
        )

        xposedModule.hook(handler.getDeclaredMethod("handleMessage", Message::class.java))
            .intercept { chain ->
                tick(chain.thisObject, (chain.args[0] as Message).what)
                null
            }
        xposedModule.hook(view.getDeclaredMethod("update", Observable::class.java, Any::class.java))
            .intercept { chain ->
                render(chain.thisObject)
                null
            }
        listOf(
            view.getDeclaredMethod("onFinishInflate"),
            view.getDeclaredMethod("scaleView", Float::class.javaPrimitiveType),
        ).forEach { method ->
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                applyStyle(chain.thisObject)
                result
            }
        }
    }

    private external fun initialize(
        content: Field, updated: Field, manager: Field, layout: Int, up: String, down: String,
        bits: Boolean, base: Int, showPerSecond: Boolean,
        textSize: Float, markerSize: Float, markerGap: Float,
        lineSpacing: Float, threshold: Int, interval: Long, paddingEnd: Float,
    )
    private external fun tick(handler: Any, what: Int)
    private external fun render(view: Any)
    private external fun applyStyle(view: Any)
}
