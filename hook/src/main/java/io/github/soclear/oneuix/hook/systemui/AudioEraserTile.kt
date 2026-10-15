package io.github.soclear.oneuix.hook.systemui

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.AudioEraserTileState
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AudioEraserTile {
    private const val SERVICE_CLASS = "io.github.soclear.oneuix.AudioEraserTileService"
    private const val BANNER_PACKAGE = "com.android.systemui.samsung.quicksetting.ui.banner."

    context(xposedModule: XposedModule)
    fun hideBanner() = afterAttachTry(ONE_UI_VERSION >= 90000) {
        val bannerTransform = classLoader.loadClass(BANNER_PACKAGE + "TopBannerViewModel\$1\$1")
            .declaredMethods.single { it.name == "invoke" && it.parameterCount == 5 }
        // Hiding the banner needs no controller, playback tracking, or tile hooks.
        xposedModule.hook(bannerTransform).intercept { chain ->
            val args = chain.args.toTypedArray()
            args[3] = false
            chain.proceed(args)
        }
    }

    context(xposedModule: XposedModule)
    fun install() = afterAttachTry(ONE_UI_VERSION >= 90000) {
        Bridge(xposedModule, classLoader).install()
    }

    private class Bridge(
        private val module: XposedModule,
        private val loader: ClassLoader,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val tiles = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
        private val reportedFailure = AtomicBoolean()
        private val customTileClass = loader.loadClass("com.android.systemui.qs.external.CustomTile")
        private val nativeClass = loader.loadClass(BANNER_PACKAGE + "AudioEraser")
        private val onModeClass = loader.loadClass(BANNER_PACKAGE + "AudioEraser\$Mode\$On")
        private val offModeClass = loader.loadClass(BANNER_PACKAGE + "AudioEraser\$Mode\$Off")
        @Volatile private var controller = WeakReference<Any>(null)
        @Volatile private var ready = false

        private val refreshTiles = Runnable {
            val snapshot = synchronized(tiles) { tiles.toList() }
            snapshot.forEach { tile -> safely { tile.reflect.call("refreshState", null) } }
        }

        fun install() {
            val expandable = loader.loadClass("com.android.systemui.animation.Expandable")
            val stateClass = loader.loadClass("com.android.systemui.plugins.qs.QSTile\$State")
            val constructor = nativeClass.declaredConstructors.single()
            val updateState = customTileClass.getDeclaredMethod("handleUpdateState", stateClass, Any::class.java)
            val click = customTileClass.getDeclaredMethod("handleClick", expandable)
            val detailAdapter = customTileClass.getDeclaredMethod("getDetailAdapter")
            val baseClass = requireNotNull(customTileClass.superclass)
            val longClick = baseClass.reflect.findMethodExact("handleLongClick", expandable)
            val secondaryClick = baseClass.reflect.findMethodExact("handleSecondaryClick", expandable)
            val updates = nativeClass.declaredMethods.filter {
                it.name in setOf("updateEnabled", "updateMode", "updatePackageName", "updatePackageLabel", "updatePlaybackAllowed")
            }
            require(updates.size == 5)
            listOf("_enabled", "_mode", "_packageName", "_packageLabel", "_playbackAllowed", "detailAdapter")
                .forEach { nativeClass.getDeclaredField(it) }
            customTileClass.getDeclaredField("mComponent")
            listOf("state", "label", "secondaryLabel", "contentDescription", "handlesLongClick", "handlesSecondaryClick", "dualTarget")
                .forEach { stateClass.getDeclaredField(it) }

            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                safely {
                    val native = requireNotNull(chain.thisObject)
                    controller = WeakReference(native)
                    available(native)
                    ready = true
                    scheduleRefresh()
                }
                result
            }
            updates.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    scheduleRefresh()
                    result
                }
            }
            module.hook(updateState).intercept { chain ->
                val result = chain.proceed()
                val tile = chain.thisObject
                if (tile != null && isOurTile(tile)) safely {
                    synchronized(tiles) { tiles.add(tile) }
                    val native = controller.get()
                    val status = if (ready && native != null) nativeState(native) else AudioEraserTileState.UNAVAILABLE
                    val available = status != AudioEraserTileState.UNAVAILABLE
                    val active = status == AudioEraserTileState.ACTIVE
                    val state = requireNotNull(chain.args[0])
                    state.reflect["state"] = status
                    // The editable service is toggleable; keep accessibility's BooleanState in sync.
                    if (state.javaClass.simpleName == "BooleanState") {
                        state.reflect["value"] = active
                    }
                    if (native != null) {
                        state.reflect["label"] = native.reflect.call("getTitle")
                        state.reflect["secondaryLabel"] = if (available) value(native, "_packageLabel") else null
                    }
                    state.reflect["contentDescription"] = state.reflect["label"]
                    state.reflect["handlesLongClick"] = available
                    state.reflect["handlesSecondaryClick"] = available
                    state.reflect["dualTarget"] = available
                }
                result
            }
            module.hook(click).intercept { chain ->
                if (chain.thisObject?.let(::isOurTile) == true) {
                    performAction("performIconClickAction")
                    null
                } else chain.proceed()
            }
            listOf(longClick, secondaryClick).distinct().forEach { method ->
                module.hook(method).intercept { chain ->
                    if (chain.thisObject?.let(::isOurTile) == true) {
                        performAction("performIconLongClickAction")
                        null
                    } else chain.proceed()
                }
            }
            module.hook(detailAdapter).intercept { chain ->
                if (chain.thisObject?.let(::isOurTile) == true) {
                    controller.get()?.takeIf { ready && available(it) }?.reflect?.get("detailAdapter")
                } else chain.proceed()
            }
            module.hook(customTileClass.getDeclaredMethod("handleDestroy")).intercept { chain ->
                synchronized(tiles) { tiles.remove(chain.thisObject) }
                chain.proceed()
            }
        }

        private fun isOurTile(tile: Any): Boolean {
            if (!customTileClass.isInstance(tile)) return false
            val component = tile.reflect["mComponent"] as? ComponentName ?: return false
            return component.packageName == BuildConfig.MODULE_APPLICATION_ID && component.className == SERVICE_CLASS
        }

        private fun value(native: Any, field: String): Any? =
            native.reflect.getNonNull<Any>(field).reflect.call("getValue")

        private fun available(native: Any): Boolean = nativeState(native) != AudioEraserTileState.UNAVAILABLE

        private fun nativeState(native: Any): Int {
            // Read the same live StateFlows as Samsung's banner, not their persisted preferences.
            val mode = value(native, "_mode")
            return AudioEraserTileState.resolve(
                enabled = value(native, "_enabled") == true,
                playbackAllowed = value(native, "_playbackAllowed") == true,
                packageName = value(native, "_packageName") as? String,
                mode = when {
                    onModeClass.isInstance(mode) -> 1
                    offModeClass.isInstance(mode) -> 0
                    else -> 2
                }
            )
        }

        private fun performAction(field: String) {
            handler.post {
                safely {
                    val native = controller.get() ?: return@safely
                    if (!ready || !available(native)) return@safely
                    val function0 = loader.loadClass("kotlin.jvm.functions.Function0")
                    val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
                    val callback = Proxy.newProxyInstance(loader, arrayOf(function0)) { proxy, method, args ->
                        when (method.name) {
                            "invoke" -> unit
                            "hashCode" -> System.identityHashCode(proxy)
                            "equals" -> proxy === args?.firstOrNull()
                            else -> "OneUIX Audio Eraser action"
                        }
                    }
                    native.reflect.getNonNull<Any>(field).reflect.call("invoke", callback)
                }
            }
        }

        private fun scheduleRefresh() {
            handler.removeCallbacks(refreshTiles)
            handler.post(refreshTiles)
        }

        private inline fun safely(action: () -> Unit) {
            try {
                action()
            } catch (t: Throwable) {
                ready = false
                if (reportedFailure.compareAndSet(false, true)) with(module) { xlog(t) }
            }
        }
    }
}
