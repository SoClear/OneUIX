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
import java.lang.reflect.Modifier
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
// 语法糖：支持 clazz["fieldName"] = value 访问！
// 语法糖：获取字段值
operator fun Any.get(name: String): Any? {
    val field = findField(name)
    val isStatic = Modifier.isStatic(field.modifiers)
    // 静态字段 target 必须为 null；实例字段不能直接在 Class 对象上 get
    val target = if (isStatic) null else this
    return field.get(target)
}

// 语法糖：设置字段值
operator fun Any.set(name: String, value: Any?) {
    val field = findField(name)
    val isStatic = Modifier.isStatic(field.modifiers)
    val target = if (isStatic) null else this
    field.set(target, value)
}

// 基本类型与其包装类的映射，用于反射参数匹配
private val boxedTypes: Map<Class<*>, Class<*>> = mapOf(
    Boolean::class.java to Boolean::class.javaObjectType,
    Byte::class.java to Byte::class.javaObjectType,
    Char::class.java to Char::class.javaObjectType,
    Short::class.java to Short::class.javaObjectType,
    Int::class.java to Int::class.javaObjectType,
    Long::class.java to Long::class.javaObjectType,
    Float::class.java to Float::class.javaObjectType,
    Double::class.java to Double::class.javaObjectType,
)

private fun Class<*>.boxed(): Class<*> = boxedTypes[this] ?: this

private fun isCompatible(parameterType: Class<*>, argument: Any?): Boolean = when {
    argument == null -> !parameterType.isPrimitive
    else -> parameterType.boxed().isInstance(argument)
}

// 按方法名和参数运行时类型匹配，在自身及父类中查找并调用实例方法
fun Any.callMethod(name: String, vararg args: Any?): Any? {
    var current: Class<*>? = this.javaClass
    while (current != null && current != Any::class.java) {
        current.declaredMethods
            .filter { it.name == name && it.parameterTypes.size == args.size }
            .forEach { method ->
                if (method.parameterTypes.withIndex().all { (index, type) ->
                        isCompatible(type, args[index])
                    }
                ) {
                    return method.apply { isAccessible = true }.invoke(this, *args)
                }
            }
        current = current.superclass
    }
    throw NoSuchMethodException("Method '$name'(${args.size} args) not found in ${this.javaClass.name}")
}

// 按方法名和参数运行时类型匹配，在自身及父类中查找并调用静态方法
fun Class<*>.callStaticMethod(name: String, vararg args: Any?): Any? {
    var current: Class<*>? = this
    while (current != null && current != Any::class.java) {
        current.declaredMethods
            .filter { it.name == name && Modifier.isStatic(it.modifiers) && it.parameterTypes.size == args.size }
            .forEach { method ->
                if (method.parameterTypes.withIndex().all { (index, type) ->
                        isCompatible(type, args[index])
                    }
                ) {
                    return method.apply { isAccessible = true }.invoke(null, *args)
                }
            }
        current = current.superclass
    }
    throw NoSuchMethodException("Static method '$name'(${args.size} args) not found in ${this.name}")
}

// 按构造函数参数运行时类型匹配，创建实例
fun Class<*>.newInstance(vararg args: Any?): Any {
    declaredConstructors
        .filter { it.parameterTypes.size == args.size }
        .forEach { constructor ->
            if (constructor.parameterTypes.withIndex().all { (index, type) ->
                    isCompatible(type, args[index])
                }
            ) {
                return constructor.apply { isAccessible = true }.newInstance(*args)
            }
        }
    throw NoSuchMethodException("Constructor(${args.size} args) not found in $name")
}

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun getSystemContext(): Context {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
    val currentActivityThread = currentActivityThreadMethod.invoke(null)
    val getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext")
    return getSystemContextMethod.invoke(currentActivityThread) as Context
}

@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
fun currentApplication(): Application {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication")
    return (currentApplicationMethod.invoke(null) as? Application)
        ?: (getSystemContext() as Application)
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
        xposedModule.log(priority, null, sb.toString(), actualThrowable)
    } else {
        xposedModule.log(priority, null, sb.toString())
    }
}
