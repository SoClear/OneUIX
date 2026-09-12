package io.github.soclear.oneuix.hook.util

import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.content.Context.CONTEXT_IGNORE_SECURITY
import android.content.Context.MODE_PRIVATE
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File

fun getSystemContext(): Context {
    val activityThreadClass = findClass("android.app.ActivityThread", null)
    val currentActivityThread = callStaticMethod(activityThreadClass, "currentActivityThread")
    return callMethod(currentActivityThread, "getSystemContext") as Context
}

fun Context.createCurrentContext(): Context = createPackageContext(
    AndroidAppHelper.currentPackageName(),
    CONTEXT_IGNORE_SECURITY
)

fun getCurrentSharedPreferences(name: String): SharedPreferences = getSystemContext()
    .createCurrentContext()
    .getSharedPreferences(name, MODE_PRIVATE)

fun LoadPackageParam.getSharedPreferences(name: String): SharedPreferences = getSystemContext()
    .createPackageContext(packageName, CONTEXT_IGNORE_SECURITY)
    .getSharedPreferences(name, MODE_PRIVATE)

fun getPackageVersionCode(name: String = AndroidAppHelper.currentPackageName()): Long =
    getSystemContext().packageManager.getPackageInfo(name, 0).longVersionCode

val Context.longVersionCode get() = packageManager.getPackageInfo(packageName, 0).longVersionCode

fun afterAttach(action: Context.() -> Unit) {
    val callback = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            action(param.args[0] as Context)
        }
    }
    findAndHookMethod(Application::class.java, "attach", Context::class.java, callback)
}

// 向宿主添加资源，路径为apk文件路径。例如添加模块的资源
fun addAssetPath(modulePath: String) {
    findAndHookMethod(
        ContextWrapper::class.java,
        "attachBaseContext",
        Context::class.java,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = param.thisObject as Context
                if (context !is Application) return
                try {
                    val moduleApk = File(modulePath)
                    val parcelFileDescriptor = ParcelFileDescriptor.open(moduleApk, ParcelFileDescriptor.MODE_READ_ONLY)
                    val resourcesProvider = ResourcesProvider.loadFromApk(parcelFileDescriptor)
                    val resourcesLoader = ResourcesLoader()
                    resourcesLoader.addProvider(resourcesProvider)
                    context.resources.addLoaders(resourcesLoader)
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }
    )
}

fun xlog(string: String) {
    val result = "\n\n////////////////\n\n////////////////\n\n$string\n\n////////////////\n\n"
    XposedBridge.log(result)
}
