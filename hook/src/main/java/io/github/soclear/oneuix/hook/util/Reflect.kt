package io.github.soclear.oneuix.hook.util

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Array as JavaArray
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

// -------------------------------------------------------------
// 高性能并发反射缓存池 (FieldKey / MethodKey / ConstructorKey)
// -------------------------------------------------------------
private class FieldKey(
    val clazz: Class<*>,
    val name: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FieldKey) return false
        return clazz == other.clazz && name == other.name
    }

    override fun hashCode(): Int = 31 * clazz.hashCode() + name.hashCode()
}

private class MethodKey(
    val clazz: Class<*>,
    val name: String,
    val paramTypes: Array<Class<*>>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodKey) return false
        return clazz == other.clazz && name == other.name && paramTypes.contentEquals(other.paramTypes)
    }

    override fun hashCode(): Int = 31 * (31 * clazz.hashCode() + name.hashCode()) + paramTypes.contentHashCode()
}

private class ConstructorKey(
    val clazz: Class<*>,
    val paramTypes: Array<Class<*>>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConstructorKey) return false
        return clazz == other.clazz && paramTypes.contentEquals(other.paramTypes)
    }

    override fun hashCode(): Int = 31 * clazz.hashCode() + paramTypes.contentHashCode()
}

private val fieldCache = ConcurrentHashMap<FieldKey, Field>()
private val methodCache = ConcurrentHashMap<MethodKey, Method>()
private val constructorCache = ConcurrentHashMap<ConstructorKey, Constructor<*>>()

private val EMPTY_CLASS_ARRAY = emptyArray<Class<*>>()

// 基本类型名与常用别名解析（消除运行时 Map 对象分配，无需 !!）
private fun resolvePrimitiveOrAlias(name: String): Class<*>? = when (name) {
    "boolean" -> Boolean::class.java
    "byte" -> Byte::class.java
    "char" -> Char::class.java
    "short" -> Short::class.java
    "int" -> Int::class.java
    "long" -> Long::class.java
    "float" -> Float::class.java
    "double" -> Double::class.java
    "void" -> Void.TYPE
    "string", "String" -> String::class.java
    "object", "Object" -> Any::class.java
    else -> null
}

/**
 * 基本类型装箱匹配（利用 isPrimitive 极速短路，分支直比指针，零 Map 分配）
 */
private fun Class<*>.boxed(): Class<*> {
    if (!isPrimitive) return this
    return when (this) {
        Boolean::class.java -> Boolean::class.javaObjectType
        Byte::class.java -> Byte::class.javaObjectType
        Char::class.java -> Char::class.javaObjectType
        Short::class.java -> Short::class.javaObjectType
        Int::class.java -> Int::class.javaObjectType
        Long::class.java -> Long::class.javaObjectType
        Float::class.java -> Float::class.javaObjectType
        Double::class.java -> Double::class.javaObjectType
        Void.TYPE -> Void::class.javaObjectType
        else -> this
    }
}

/**
 * 完整符合 JLS 5.1.2 规范的基本类型加宽规则（包含 Char）
 */
private fun isWideningCompatible(targetType: Class<*>, arg: Any?): Boolean {
    if (!targetType.isPrimitive) return false
    if (arg is Char) {
        return targetType == Int::class.java ||
               targetType == Long::class.java ||
               targetType == Float::class.java ||
               targetType == Double::class.java
    }
    if (arg !is Number) return false
    return when (targetType) {
        Double::class.java -> arg is Float || arg is Long || arg is Int || arg is Short || arg is Byte
        Float::class.java -> arg is Long || arg is Int || arg is Short || arg is Byte
        Long::class.java -> arg is Int || arg is Short || arg is Byte
        Int::class.java -> arg is Short || arg is Byte
        Short::class.java -> arg is Byte
        else -> false
    }
}

/**
 * 校验类型是否兼容（精确多态 + 装箱 + JLS 规范加宽）
 */
private fun isCompatible(parameterType: Class<*>, argument: Any?): Boolean = when {
    argument == null -> !parameterType.isPrimitive
    else -> parameterType.boxed().isInstance(argument) || isWideningCompatible(parameterType, argument)
}

/**
 * 工业级 ClassLoader 智能加载：
 * 1. 数组类型自动解析（如 "int[]", "byte[]", "java.lang.String[]"）
 * 2. 基本数据类型映射（如 "int" -> int.class）
 * 3. 智能双亲 ClassLoader 候选链（preferredLoader -> contextClassLoader -> moduleLoader）
 * 4. 内部类语法糖智能容错（Outer.Inner 自动回退尝试 Outer$Inner）
 */
fun loadClassWithFallback(className: String, preferredLoader: ClassLoader? = null): Class<*> {
    if (className.endsWith("[]")) {
        val componentName = className.substring(0, className.length - 2)
        val componentType = loadClassWithFallback(componentName, preferredLoader)
        return JavaArray.newInstance(componentType, 0).javaClass
    }

    resolvePrimitiveOrAlias(className)?.let { return it }

    val loaders = listOfNotNull(
        preferredLoader,
        Thread.currentThread().contextClassLoader,
        Reflect::class.java.classLoader,
        ClassLoader.getSystemClassLoader()
    ).distinct()

    for (loader in loaders) {
        try {
            return Class.forName(className, false, loader)
        } catch (_: ClassNotFoundException) {
            if (className.contains('.')) {
                val lastDot = className.lastIndexOf('.')
                val dollarName = className.substring(0, lastDot) + '$' + className.substring(lastDot + 1)
                try {
                    return Class.forName(dollarName, false, loader)
                } catch (_: ClassNotFoundException) {}
            }
        }
    }
    throw ClassNotFoundException("Failed to resolve class '$className' via available ClassLoaders")
}

private fun Any.toJavaClass(preferredLoader: ClassLoader? = null): Class<*> = when (this) {
    is Class<*> -> this
    is KClass<*> -> this.java
    is String -> loadClassWithFallback(this, preferredLoader)
    else -> throw IllegalArgumentException("Unsupported type: ${this.javaClass.name}, expected Class<*>, KClass<*>, or String")
}

/**
 * 现代高性能 Kotlin 反射门面
 */
@JvmInline
value class Reflect(val target: Any) {

    val targetClass: Class<*>
        get() = (target as? Class<*>) ?: target.javaClass

    val isClass: Boolean
        get() = target is Class<*>

    private fun checkInstanceAccess(member: Member) {
        if (isClass && !Modifier.isStatic(member.modifiers)) {
            throw IllegalStateException("Cannot access instance member '${member.name}' on a Class target ($targetClass). Use instance.reflect instead.")
        }
    }

    // ==========================================
    // 字段操作 (Field)
    // ==========================================

    fun findField(name: String): Field {
        val clazz = targetClass
        val key = FieldKey(clazz, name)
        return fieldCache.getOrPut(key) {
            var current: Class<*>? = clazz
            while (current != null) {
                try {
                    return@getOrPut current.getDeclaredField(name).apply { isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    current = current.superclass
                }
            }
            try {
                return@getOrPut clazz.getField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {}

            throw NoSuchFieldException("Field '$name' not found in $clazz")
        }
    }

    operator fun get(name: String): Any? {
        val field = findField(name)
        checkInstanceAccess(field)
        val isStatic = Modifier.isStatic(field.modifiers)
        return field.get(if (isStatic) null else target)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getAs(name: String): T? = get(name) as? T

    @Suppress("UNCHECKED_CAST")
    fun <T> getNonNull(name: String): T =
        get(name) as? T ?: throw NullPointerException("Field '$name' is null or wrong type in $targetClass")

    operator fun set(name: String, value: Any?) {
        val field = findField(name)
        checkInstanceAccess(field)
        val isStatic = Modifier.isStatic(field.modifiers)
        field.set(if (isStatic) null else target, value)
    }

    // ==========================================
    // 方法操作 (Method)
    // ==========================================

    /**
     * 精确查找方法（专用于 LibXposed 注册 Hook）
     */
    fun findMethodExact(name: String, vararg paramTypes: Any): Method {
        val clazz = targetClass
        val loader = clazz.classLoader
        val resolvedTypes = if (paramTypes.isEmpty()) EMPTY_CLASS_ARRAY
            else Array(paramTypes.size) { paramTypes[it].toJavaClass(loader) }

        val key = MethodKey(clazz, name, resolvedTypes)
        return methodCache.getOrPut(key) {
            var current: Class<*>? = clazz
            while (current != null) {
                try {
                    return@getOrPut current.getDeclaredMethod(name, *resolvedTypes).apply { isAccessible = true }
                } catch (_: NoSuchMethodException) {
                    current = current.superclass
                }
            }
            try {
                return@getOrPut clazz.getMethod(name, *resolvedTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {}

            throw NoSuchMethodException("Method '$name'(${resolvedTypes.joinToString { it.name }}) not found in $clazz")
        }
    }

    /**
     * 按实参动态自适应查找方法（修复 null 实参第一阶段匹配与 JLS 加宽支持）
     */
    fun findMethod(name: String, vararg args: Any?): Method {
        val clazz = targetClass
        val argTypes = if (args.isEmpty()) EMPTY_CLASS_ARRAY 
            else Array<Class<*>>(args.size) { (args[it]?.javaClass ?: Any::class.java) as Class<*> }
        val key = MethodKey(clazz, name, argTypes)
        
        return methodCache.getOrPut(key) {
            // 第一阶段：整树寻找精准匹配（修复对 null 实参的精准兼容）
            var current: Class<*>? = clazz
            while (current != null) {
                current.declaredMethods
                    .filter { it.name == name && it.parameterTypes.size == args.size }
                    .firstOrNull { method ->
                        method.parameterTypes.indices.all { i ->
                            val arg = args[i]
                            if (arg == null) !method.parameterTypes[i].isPrimitive 
                            else method.parameterTypes[i].boxed() == arg.javaClass
                        }
                    }?.let { return@getOrPut it.apply { isAccessible = true } }
                current = current.superclass
            }

            // 第二阶段：整树多态与 JLS 数值加宽降级匹配
            current = clazz
            while (current != null) {
                current.declaredMethods
                    .filter { it.name == name && it.parameterTypes.size == args.size }
                    .firstOrNull { method ->
                        method.parameterTypes.indices.all { i ->
                            isCompatible(method.parameterTypes[i], args[i])
                        }
                    }?.let { return@getOrPut it.apply { isAccessible = true } }
                current = current.superclass
            }

            // 第三阶段：兜底公共接口方法（含 Java 8+ 默认方法）
            clazz.methods
                .filter { it.name == name && it.parameterTypes.size == args.size }
                .firstOrNull { method ->
                    method.parameterTypes.indices.all { i ->
                        isCompatible(method.parameterTypes[i], args[i])
                    }
                }?.let { return@getOrPut it.apply { isAccessible = true } }

            throw NoSuchMethodException("Method '$name'(${args.size} args) not found in $clazz")
        }
    }

    fun findMethod(predicate: (Method) -> Boolean): Method? {
        var current: Class<*>? = targetClass
        while (current != null) {
            current.declaredMethods.firstOrNull(predicate)?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    fun call(name: String, vararg args: Any?): Any? {
        val method = findMethod(name, *args)
        checkInstanceAccess(method)
        val isStatic = Modifier.isStatic(method.modifiers)
        return method.invoke(if (isStatic) null else target, *args)
    }

    /**
     * 精确调用方法（使用 Array<out Any> 允许 Array<Class<*>> 或 Array<String> 传入）
     */
    fun callExact(name: String, paramTypes: Array<out Any>, vararg args: Any?): Any? {
        require(paramTypes.size == args.size) { "paramTypes.size (${paramTypes.size}) != args.size (${args.size})" }
        val method = findMethodExact(name, *paramTypes)
        checkInstanceAccess(method)
        val isStatic = Modifier.isStatic(method.modifiers)
        return method.invoke(if (isStatic) null else target, *args)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callAs(name: String, vararg args: Any?): T? = call(name, *args) as? T

    @Suppress("UNCHECKED_CAST")
    fun <T> callExactAs(name: String, paramTypes: Array<out Any>, vararg args: Any?): T? =
        callExact(name, paramTypes, *args) as? T

    operator fun invoke(name: String, vararg args: Any?): Any? = call(name, *args)

    // ==========================================
    // 构造函数与实例化 (Constructor)
    // ==========================================

    fun findConstructorExact(vararg paramTypes: Any): Constructor<*> {
        if (!isClass) {
            throw UnsupportedOperationException("Cannot find constructor on an instance. Target must be a Class<*>.")
        }
        val clazz = target as Class<*>
        val loader = clazz.classLoader
        val resolvedTypes = if (paramTypes.isEmpty()) EMPTY_CLASS_ARRAY
            else Array(paramTypes.size) { paramTypes[it].toJavaClass(loader) }

        val key = ConstructorKey(clazz, resolvedTypes)
        return constructorCache.getOrPut(key) {
            clazz.getDeclaredConstructor(*resolvedTypes).apply { isAccessible = true }
        }
    }

    fun new(vararg args: Any?): Any {
        if (!isClass) {
            throw UnsupportedOperationException("Cannot call new() on an instance. Target must be a Class<*>.")
        }
        val clazz = target as Class<*>
        val argTypes = if (args.isEmpty()) EMPTY_CLASS_ARRAY 
            else Array<Class<*>>(args.size) { (args[it]?.javaClass ?: Any::class.java) as Class<*> }
        val key = ConstructorKey(clazz, argTypes)
        
        val constructor = constructorCache.getOrPut(key) {
            clazz.declaredConstructors
                .filter { it.parameterTypes.size == args.size }
                .firstOrNull { ctor ->
                    ctor.parameterTypes.indices.all { i ->
                        val arg = args[i]
                        if (arg == null) !ctor.parameterTypes[i].isPrimitive 
                        else ctor.parameterTypes[i].boxed() == arg.javaClass
                    }
                }?.apply { isAccessible = true }
                ?: clazz.declaredConstructors
                    .filter { it.parameterTypes.size == args.size }
                    .firstOrNull { ctor ->
                        ctor.parameterTypes.indices.all { i ->
                            isCompatible(ctor.parameterTypes[i], args[i])
                        }
                    }?.apply { isAccessible = true }
                ?: throw NoSuchMethodException("Constructor(${args.size} args) not found in $clazz")
        }
        return constructor.newInstance(*args)
    }

    fun newExact(paramTypes: Array<out Any>, vararg args: Any?): Any {
        require(paramTypes.size == args.size) { "paramTypes.size (${paramTypes.size}) != args.size (${args.size})" }
        val constructor = findConstructorExact(*paramTypes)
        return constructor.newInstance(*args)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> newAs(vararg args: Any?): T = new(*args) as T

    @Suppress("UNCHECKED_CAST")
    fun <T> newExactAs(paramTypes: Array<out Any>, vararg args: Any?): T = newExact(paramTypes, *args) as T
}

// -------------------------------------------------------------
// 扩展入口
// -------------------------------------------------------------
inline val Any.reflect: Reflect get() = Reflect(this)
inline val KClass<*>.reflect: Reflect get() = Reflect(this.java)

fun ClassLoader.reflect(className: String): Reflect {
    val clazz = loadClassWithFallback(className, this)
    return Reflect(clazz)
}
