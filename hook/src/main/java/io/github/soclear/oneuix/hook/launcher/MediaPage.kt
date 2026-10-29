package io.github.soclear.oneuix.hook.launcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.view.WindowInsets
import android.view.WindowManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.reflect
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File

/** Add our own provider to One UI Home's Samsung overlay channel, not Discover's. */
object MediaPage {
    private const val SAMSUNG = "com.samsung.android.app.spage"
    private const val BIND = "$SAMSUNG.action.BIND_PAGE_OVERLAY"
    private val modulePackage = BuildConfig.MODULE_APPLICATION_ID
    @Volatile private var selected = false

    context(module: XposedModule)
    fun embedAppInMediaPage() = afterAttachTry {
        val config = getHookConfig(File(filesDir, "MediaPageHook.json")) {
            System.loadLibrary("dexkit")
            DexKitBridge.create(classLoader, true).use { bridge ->
                val update = bridge.findMethod {
                    matcher {
                        name = "updateMetadata"
                        usingEqStrings("SEC_FLOATING_FEATURE_LAUNCHER_CONFIG_ZERO_PAGE_PACKAGE_NAMES")
                    }
                }.singleOrNull() ?: return@use null
                val setup = bridge.findMethod {
                    matcher { usingEqStrings("set up ") }
                }.singleOrNull() ?: return@use null
                val callback = bridge.findMethod {
                    matcher { name = "onTransact"; usingEqStrings("onOverlayStateChanged state = ") }
                }.singleOrNull() ?: return@use null
                Config(longVersionCode, update.toDexMethod().serialize(), setup.toDexMethod().serialize(),
                    callback.toDexMethod().serialize())
            }
        } ?: return@afterAttachTry
        val update = DexMethod(config.update).getMethodInstance(classLoader)
        val setup = DexMethod(config.setup).getMethodInstance(classLoader)
        val title = update.declaringClass.getDeclaredMethod("getMinusOneAppTitle", Context::class.java)
        val state = update.declaringClass.declaredFields.single {
            it.type.name == "kotlinx.coroutines.flow.MutableStateFlow"
        }.apply { isAccessible = true }
        val bind = ContextWrapper::class.java.getDeclaredMethod(
            "bindService", Intent::class.java, ServiceConnection::class.java, Int::class.javaPrimitiveType
        )
        val controller = setup.declaringClass
        val provider = controller.declaredFields.single { it.type == String::class.java }
            .apply { isAccessible = true }
        val normalize = controller.declaredMethods.single {
            it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        val component = ComponentName(modulePackage, "io.github.soclear.oneuix.MainActivity")
        val resources = packageManager.getResourcesForApplication(modulePackage)
        val preview = resources.getIdentifier("media_page_preview", "drawable", modulePackage)
        check(preview != 0)
        val resourceClass = classLoader.loadClass("com.honeyspace.common.data.minusonepage.ResourceData")
        val entry = resourceClass.getConstructor(
            ComponentName::class.java, String::class.java, Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).newInstance(component, "OneUIX", preview, false)

        // The launcher owns physical display 0's bars, even while the app focuses another display.
        val main = Handler(Looper.getMainLooper())
        var activity: Activity? = null
        var full = false
        var savedFlags = 0
        var savedBars = 0
        var savedBehavior = 0
        fun fullscreen(value: Boolean) {
            if (full == value) return
            full = value
            activity?.window?.apply {
                val flag = WindowManager.LayoutParams.FLAG_FULLSCREEN
                val bars = WindowInsets.Type.systemBars()
                val insets = decorView.windowInsetsController
                if (value) {
                    savedFlags = attributes.flags and flag
                    savedBars = listOf(WindowInsets.Type.statusBars(), WindowInsets.Type.navigationBars())
                        .filter { decorView.rootWindowInsets?.isVisible(it) == true }.fold(0, Int::or)
                    savedBehavior = insets?.systemBarsBehavior ?: 0
                    addFlags(flag)
                    insets?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insets?.hide(bars)
                } else {
                    setFlags(savedFlags, flag)
                    insets?.systemBarsBehavior = savedBehavior
                    insets?.hide(bars and savedBars.inv())
                    insets?.show(savedBars)
                }
            }
        }
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).setPackage(packageName), 0)!!.activityInfo
        val homeClass = classLoader.loadClass(home.targetActivity ?: home.name)
        module.hook(homeClass.getDeclaredMethod("onCreate", Bundle::class.java)).intercept { chain ->
            chain.proceed().also { activity = chain.thisObject as Activity }
        }
        module.hook(DexMethod(config.callback).getMethodInstance(classLoader)).intercept { chain ->
            val code = chain.args[0] as Int
            if (selected && code in 2..3) {
                val data = chain.args[1] as Parcel
                val position = data.dataPosition()
                val value = try {
                    data.enforceInterface("com.samsung.android.app.spage.service.IPageOverlayCallback")
                    data.readInt()
                } finally { data.setDataPosition(position) }
                main.post {
                    if (code == 3) fullscreen(value != 0)
                    else if (value == 0) fullscreen(false)
                }
                if (code == 3) return@intercept true
            }
            chain.proceed()
        }

        module.hook(update).intercept { chain ->
            val result = chain.proceed()
            val utils = chain.thisObject!!
            @Suppress("UNCHECKED_CAST")
            val components = utils.reflect.call("getSupportComponents") as MutableList<ComponentName>
            if (component !in components) components.add(component)
            val flow = state.get(utils)!!
            val entries = flow.reflect.call("getValue") as List<*>
            flow.reflect.call("setValue", entries + entry)
            result
        }
        module.hook(title).intercept { chain -> if (selected) "OneUIX" else chain.proceed() }

        // The stock controller normalizes every non-Google package to Samsung. Preserve ours,
        // but reuse its Samsung mover so scrolling and Home/Recents remain native.
        module.hook(normalize).intercept { chain ->
            val own = chain.args[0] == modulePackage
            selected = own
            if (!own) main.post { fullscreen(false) }
            val result = chain.proceed()
            if (own) provider.set(chain.thisObject, modulePackage)
            result
        }
        module.hook(setup).intercept { chain ->
            val own = provider.get(chain.thisObject) == modulePackage
            if (own) provider.set(chain.thisObject, SAMSUNG)
            try {
                chain.proceed()
            } finally {
                if (own) provider.set(chain.thisObject, modulePackage)
            }
        }
        module.hook(bind).intercept { chain ->
            val intent = chain.args[0] as Intent
            if (selected && intent.action == BIND && intent.`package` == SAMSUNG) {
                chain.proceed(arrayOf(
                    Intent(intent).setComponent(ComponentName(modulePackage,
                        "io.github.soclear.oneuix.MediaPageService")).setPackage(modulePackage),
                    chain.args[1], chain.args[2]
                ))
            } else chain.proceed()
        }
    }

    @Serializable
    private data class Config(
        override val versionCode: Long,
        val update: String,
        val setup: String,
        val callback: String,
    ) : HookConfig
}
