package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection helpers with the legacy semantics ColorMotion relies on. */
public final class XposedHelpers {
    private XposedHelpers() { }

    public static final class ClassNotFoundError extends Error {
        public ClassNotFoundError(Throwable cause) {
            super(cause);
        }
    }

    public static final class InvocationTargetError extends Error {
        public InvocationTargetError(Throwable cause) {
            super(cause);
        }
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false,
                    classLoader != null ? classLoader : XposedHelpers.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(e);
        }
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + fieldName);
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        Class<?>[] types = new Class<?>[parameterTypes.length];
        for (int i = 0; i < types.length; i++) {
            Object type = parameterTypes[i];
            types[i] = type instanceof Class ? (Class<?>) type
                    : findClass((String) type, clazz.getClassLoader());
        }
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(methodName, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
    }

    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName,
                                         Object... parameterTypes) {
        return findMethodExact(findClass(className, classLoader), methodName, parameterTypes);
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return invoke(obj.getClass(), obj, methodName, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return invoke(clazz, null, methodName, args);
    }

    private static Object invoke(Class<?> clazz, Object receiver, String methodName, Object[] args) {
        Method method = findBestMethod(clazz, methodName, args);
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    /** Same-arity method whose parameters accept the runtime arguments, closest types first. */
    private static Method findBestMethod(Class<?> clazz, String name, Object[] args) {
        Method best = null;
        long bestScore = Long.MAX_VALUE;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                long score = score(method.getParameterTypes(), args);
                if (score >= 0 && score < bestScore) {
                    best = method;
                    bestScore = score;
                }
            }
            if (best != null) break;
        }
        if (best == null) throw new NoSuchMethodError(clazz.getName() + "#" + name);
        best.setAccessible(true);
        return best;
    }

    private static long score(Class<?>[] parameters, Object[] args) {
        long total = 0;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = box(parameters[i]);
            Object arg = args[i];
            if (arg == null) {
                if (parameters[i].isPrimitive()) return -1;
                continue;
            }
            if (!parameter.isInstance(arg)) return -1;
            int distance = 0;
            for (Class<?> c = arg.getClass(); c != null && c != parameter; c = c.getSuperclass()) distance++;
            total += distance;
        }
        return total;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
