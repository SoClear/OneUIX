package io.github.soclear.oneuix.hook.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

// 简单的 Field 缓存池，避免高频调用导致的反射性能开销
private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, Field>()

fun Any.findField(name: String): Field {
    val clazz = this as? Class<*> ?: this.javaClass
    val key = clazz to name
    return fieldCache.getOrPut(key) {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return@getOrPut current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("Field '$name' not found in $clazz")
    }
}

// 语法糖：支持 obj["fieldName"] = value 访问！
operator fun Any.set(name: String, value: Any?) =  findField(name).set(this, value)
operator fun Any.get(name: String): Any? = findField(name).get(this)

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun getSystemContext(): Context {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
    val currentActivityThread = currentActivityThreadMethod.invoke(null)
    val getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext")
    return getSystemContextMethod.invoke(currentActivityThread) as Context
}

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun getCurrentPackageName(): String {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentPackageNameMethod = activityThreadClass.getDeclaredMethod("currentPackageName")
    return (currentPackageNameMethod.invoke(null) as? String) ?: ""
}

fun getPackageVersionCode(name: String = getCurrentPackageName()): Long {
    if (name.isEmpty()) return -1L
    return getSystemContext().packageManager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0)).longVersionCode
}

val Context.longVersionCode get() = packageManager.getPackageInfo(packageName, 0).longVersionCode

@SuppressLint("DiscouragedPrivateApi")
context(xposedModule: XposedModule)
fun afterAttach(action: Context.() -> Unit) {
    val method = Application::class.java.getDeclaredMethod("attach", Context::class.java)
    xposedModule.hook(method).intercept { chain ->
        val result = chain.proceed()
        action(chain.args[0] as Context)
        result
    }
}

// 向宿主添加资源，路径为apk文件路径。例如添加模块的资源
context(xposedModule: XposedModule)
fun addAssetPath(modulePath: String) {
    val method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
    xposedModule.hook(method).intercept { chain ->
        val result = chain.proceed()
        val context = chain.thisObject as Context
        if (context is Application) {
            try {
                val moduleApk = File(modulePath)
                val parcelFileDescriptor = ParcelFileDescriptor.open(moduleApk, ParcelFileDescriptor.MODE_READ_ONLY)
                val resourcesProvider = ResourcesProvider.loadFromApk(parcelFileDescriptor)
                val resourcesLoader = ResourcesLoader()
                resourcesLoader.addProvider(resourcesProvider)
                context.resources.addLoaders(resourcesLoader)
            } catch (t: Throwable) {
                xposedModule.log(Log.ERROR, "Util", "addAssetPath", t)
            }
        }
        result
    }
}

context(xposedModule: XposedModule)
fun xlog(string: String) {
    val result = "\n\n////////////////\n\n////////////////\n\n$string\n\n////////////////\n\n"
    // 直接在当前宿主进程打印：保证 adb logcat 按目标进程过滤时可见，且在注入极早期
    // （onModuleLoaded / Application.attach 回调）也可用，不依赖框架的跨进程日志 Binder。
    Log.println(Log.DEBUG, "xlog", result)
    // 同时交给框架，方便在 Xposed 管理器的模块日志里查看。
    xposedModule.log(Log.DEBUG, "xlog", result)
}
