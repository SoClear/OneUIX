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
        val updateSettings = observer.declaredMethods.firstOrNull {
            it.name == "updateSettingValues" && it.parameterCount == 0
        }
        if (updateSettings == null) {
            // One UI 8.5 及以下
            val basicRune = Class.forName("com.android.systemui.BasicRune", true, classLoader)
            basicRune.getDeclaredField("STATUS_REAL_TIME_NETWORK_SPEED").apply {
                isAccessible = true
            }.setBoolean(null, true)
            val settingsHelper = classLoader.loadClass("com.android.systemui.util.SettingsHelper")
            xposedModule.hook(settingsHelper.getDeclaredMethod("isShowNetworkSpeedInStatusBar"))
                .intercept { true }
            return@afterAttachTry
        }
        xposedModule.hook(updateSettings).intercept { chain ->
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
