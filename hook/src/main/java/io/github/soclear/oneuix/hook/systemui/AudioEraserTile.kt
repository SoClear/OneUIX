package io.github.soclear.oneuix.hook.systemui

import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.widget.Switch
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.AudioEraserTileState
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/** Bridges our editable CustomTile to the existing One UI 9 AudioEraser instance. */
object AudioEraserTile {
    private const val BANNER_PACKAGE = "com.android.systemui.samsung.quicksetting.ui.banner."
    private const val TILE_SERVICE = "io.github.soclear.oneuix.AudioEraserTileService"

    context(xposedModule: XposedModule)
    fun install() = afterAttachTry(ONE_UI_VERSION >= 90000) {
        Bridge { xlog("Audio eraser QS tile", it) }.install(xposedModule, classLoader)
    }

    private class Bridge(private val report: (Throwable) -> Unit) {
        private val main = Handler(Looper.getMainLooper())
        private val tiles = Collections.synchronizedMap(WeakHashMap<Any, Unit>())
        @Volatile private var audioEraser: Any? = null
        @Volatile private var hooksReady = false
        private var preferences: SharedPreferences? = null
        private var reportedError = false
        private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshTiles()
        }
        private val refresh = Runnable {
            val snapshot = synchronized(tiles) { tiles.keys.toList() }
            snapshot.forEach { tile -> safely { tile.reflect.call("refreshState", null) } }
        }

        fun install(module: XposedModule, loader: ClassLoader) {
            // Resolve the complete contract first. An unsupported firmware keeps its stock banner.
            val audioClass = loader.loadClass(BANNER_PACKAGE + "AudioEraser")
            val tileClass = loader.loadClass("com.android.systemui.qs.external.CustomTile")
            val samsungTileClass = loader.loadClass("com.android.systemui.qs.tileimpl.SQSTileImpl")
            val stateClass = loader.loadClass("com.android.systemui.plugins.qs.QSTile\$State")
            val expandableClass = loader.loadClass("com.android.systemui.animation.Expandable")
            val bannerTransform = loader.loadClass(BANNER_PACKAGE + "TopBannerViewModel\$1\$1")
            val transform = bannerTransform.declaredMethods.single {
                it.name == "invoke" && it.parameterCount == 5
            }
            val update = tileClass.getDeclaredMethod("handleUpdateState", stateClass, Any::class.java)
            val click = tileClass.getDeclaredMethod("handleClick", expandableClass)
            val longClick = tileClass.getDeclaredMethod("handleLongClick", expandableClass)
            val secondaryClick = samsungTileClass.getDeclaredMethod("handleSecondaryClick", expandableClass)
            val detail = tileClass.getDeclaredMethod("getDetailAdapter")
            val updateMode = audioClass.declaredMethods.single { it.name == "updateMode" }
            audioClass.getDeclaredMethod("getPreferences")
            audioClass.getDeclaredMethod("getPerformIconClickAction")
            audioClass.getDeclaredMethod("getPerformClickAction")
            audioClass.getDeclaredMethod("isIAEApp")
            audioClass.getDeclaredField("detailAdapter")
            audioClass.getDeclaredField("_mode")
            tileClass.getDeclaredField("mComponent")
            tileClass.getDeclaredField("mTile")
            val function0 = loader.loadClass("kotlin.jvm.functions.Function0")
            val function1 = loader.loadClass("kotlin.jvm.functions.Function1")
            val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
            val invoke = function1.getMethod("invoke", Any::class.java)

            audioClass.declaredConstructors.forEach { constructor ->
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    safely {
                        val controller = chain.thisObject
                        val prefs = controller.reflect.call("getPreferences") as SharedPreferences
                        synchronized(this) {
                            preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                            preferences = prefs
                            audioEraser = controller
                            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
                        }
                        refreshTiles()
                    }
                    result
                }
            }
            module.hook(updateMode).intercept { chain ->
                val result = chain.proceed()
                refreshTiles()
                result
            }
            module.hook(update).intercept { chain ->
                val result = chain.proceed()
                if (isOurTile(chain.thisObject)) {
                    tiles[chain.thisObject] = Unit
                    safely { updateTile(chain.thisObject, chain.args[0]!!) }
                }
                result
            }
            module.hook(detail).intercept { chain ->
                if (isOurTile(chain.thisObject)) {
                    // IAE apps intentionally have no extra settings in Samsung's own UI.
                    val controller = audioEraser
                    if (controller != null && tileState() != AudioEraserTileState.UNAVAILABLE &&
                        runCatching { controller.reflect.call("isIAEApp") == false }.getOrDefault(false)
                    ) {
                        runCatching { controller.reflect["detailAdapter"] }.getOrNull()
                    } else null
                } else chain.proceed()
            }
            listOf(click, longClick, secondaryClick).forEach { method ->
                module.hook(method).intercept { chain ->
                    if (isOurTile(chain.thisObject)) {
                        // QSTile callbacks run off the main thread. Samsung's detail UI requires main.
                        val isToggle = method == click
                        main.post {
                            safely {
                                val controller = audioEraser
                                if (controller != null && tileState() != AudioEraserTileState.UNAVAILABLE) {
                                    val action = controller.reflect.call(
                                        if (isToggle) "getPerformIconClickAction" else "getPerformClickAction"
                                    )
                                    val logger = Proxy.newProxyInstance(loader, arrayOf(function0)) { proxy, called, args ->
                                        when (called.name) {
                                            "invoke" -> {
                                                controller.reflect.call(if (isToggle) "logIconClickAction" else "logClickAction")
                                                unit
                                            }
                                            "toString" -> "OneUIX Audio eraser callback"
                                            "hashCode" -> System.identityHashCode(proxy)
                                            "equals" -> proxy === args?.firstOrNull()
                                            else -> null
                                        }
                                    }
                                    // Use the original actions, including Samsung account checks and policy.
                                    invoke.invoke(action, logger)
                                }
                            }
                        }
                        null
                    } else chain.proceed()
                }
            }
            hooksReady = true
            // Remove only Audio eraser from the banner's input. Other call/video banners and the
            // AudioEraser model's playback flows keep running. No Compose view/spacing hacks.
            module.hook(transform).intercept { chain ->
                if (hooksReady && audioEraser != null) {
                    val args = chain.args.toTypedArray()
                    args[3] = false
                    chain.proceed(args)
                } else chain.proceed()
            }
        }

        private fun isOurTile(tile: Any): Boolean = runCatching {
            val component = tile.reflect["mComponent"] as? ComponentName
            component?.packageName == BuildConfig.MODULE_APPLICATION_ID && component.className == TILE_SERVICE
        }.getOrDefault(false)

        private fun tileState(): Int {
            val controller = audioEraser ?: return AudioEraserTileState.UNAVAILABLE
            val prefs = preferences ?: return AudioEraserTileState.UNAVAILABLE
            val mode = runCatching {
                val value = controller.reflect["_mode"]!!.reflect.call("getValue")
                when (value?.javaClass?.simpleName) {
                    "Off" -> 0
                    "On" -> 1
                    else -> 2
                }
            }.getOrDefault(2)
            return AudioEraserTileState.resolve(
                prefs.getBoolean("audio_eraser_enabled", false),
                prefs.getString("audio_eraser_package_name", null),
                mode
            )
        }

        private fun updateTile(tile: Any, state: Any) {
            val status = tileState()
            val nativeTile = tile.reflect["mTile"] as? Tile
            nativeTile?.state = status
            state.reflect["state"] = status
            state.reflect["dualTarget"] = true
            val controller = audioEraser
            val title = controller?.reflect?.call("getTitle") as? CharSequence ?: nativeTile?.label
            val subtitle = preferences?.getString("audio_eraser_package_label", "")
                ?.takeIf { status != AudioEraserTileState.UNAVAILABLE && it.isNotBlank() }
            state.reflect["label"] = title
            state.reflect["secondaryLabel"] = subtitle
            state.reflect["contentDescription"] = listOfNotNull(title, subtitle).joinToString(", ")
            // CustomTile uses BooleanState when TOGGLEABLE_TILE metadata has been read.
            if (state.javaClass.simpleName == "BooleanState") {
                state.reflect["value"] = status == AudioEraserTileState.ACTIVE
                state.reflect["expandedAccessibilityClassName"] = Switch::class.java.name
            }
        }

        private fun refreshTiles() {
            main.removeCallbacks(refresh)
            main.post(refresh)
        }

        private inline fun safely(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                // Hooks must never propagate a compatibility failure into SystemUI.
                if (!reportedError) {
                    reportedError = true
                    report(t)
                }
            }
        }
    }
}
