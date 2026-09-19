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
import java.lang.reflect.Method

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun getSystemContext(): Context {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
    val currentActivityThread = currentActivityThreadMethod.invoke(null)
    val getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext")
    return getSystemContextMethod.invoke(currentActivityThread) as Context
}

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun currentApplication(): Application? {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication")
    return currentApplicationMethod.invoke(null) as? Application
}

fun currentContext(): Context = currentApplication() ?: getSystemContext()

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

/*
三星 ROM 的 persist.log.semlevel = 0xFFFFFF00 会屏蔽进程名包含 .sec 、.samsung 的 VERBOSE/DEBUG
所以请使用 ASSERT/ERROR/INFO/WARN

tag 传入 null，框架会自动赋予默认 Tag（Vector 为 "VectorContext"，LSPosed 为 "LSPosedContext"）
这样才能命中 Vector/LSPosed 守护进程的 Tag 白名单，同时避免自定义 Tag 被过滤
 */
context(xposedModule: XposedModule)
fun xlog(
    message: Any?,
    throwable: Throwable? = null,
    priority: Int = Log.ERROR
) {
    val moduleTag = "[OneUIX]"
    val topBorder    = "┌────────────────────────────────────────────────────────"
    val linePrefix   = "│ "
    val bottomBorder = "└────────────────────────────────────────────────────────"

    // 过滤掉 UtilKt 自身的调用帧（包含默认参数生成的 synthetic $default 方法），定位到真正的调用方
    val caller = Throwable().stackTrace.firstOrNull { frame ->
        val name = frame.className
        name != "io.github.soclear.oneuix.hook.util.UtilKt" && !name.startsWith("io.github.soclear.oneuix.hook.util.UtilKt$")
    }?.let {
        "[${it.fileName}:${it.lineNumber}] "
    }.orEmpty()

    // 巧妙兼容：如果第一个参数传的是 Throwable，且没额外传第二个 throwable 参数，自动归位
    val actualThrowable = when {
        throwable != null -> throwable
        message is Throwable -> message
        else -> null
    }

    val sb = StringBuilder().apply {
        append("\n").append(moduleTag).append(" ").append(topBorder).append("\n")

        if (message is Throwable && throwable == null) {
            // 当只传了一个 Throwable 时：第一行展示代码位置以及异常信息
            val exceptionSummary = "${message.javaClass.name}${message.message?.let { ": $it" }.orEmpty()}"
            append(moduleTag).append(" ").append(linePrefix).append(caller).append(exceptionSummary).append("\n")
        } else {
            // 传普通内容（或 message + throwable）时：逐行展示文本
            val lines = (message?.toString() ?: "null").lines()
            append(moduleTag).append(" ").append(linePrefix).append(caller).append(lines.firstOrNull().orEmpty())
                .append("\n")
            for (i in 1 until lines.size) {
                append(moduleTag).append(" ").append(linePrefix).append(lines[i]).append("\n")
            }
            if (actualThrowable != null) {
                append(moduleTag).append(" ").append(linePrefix).append("Exception: ")
                    .append(actualThrowable.javaClass.name).append(": ").append(actualThrowable.message).append("\n")
            }
        }

        // 打印堆栈
        if (actualThrowable != null) {
            actualThrowable.stackTrace.take(15).forEach { frame ->
                append(moduleTag).append(" ").append(linePrefix).append("    at ").append(frame).append("\n")
            }
            if (actualThrowable.stackTrace.size > 15) {
                append(moduleTag).append(" ").append(linePrefix)
                    .append("    ... and ${actualThrowable.stackTrace.size - 15} more frames\n")
            }
        }
        append(moduleTag).append(" ").append(bottomBorder)
    }

    // tag 必须为 null，确保 Vector/LSPosed 守护进程的白名单能正常收集到 modules 日志
    if (actualThrowable != null) {
        android.util.Log.e("OneUIX", sb.toString(), actualThrowable)
        xposedModule.log(priority, null, sb.toString(), actualThrowable)
    } else {
        android.util.Log.e("OneUIX", sb.toString())
        xposedModule.log(priority, null, sb.toString())
    }
}
