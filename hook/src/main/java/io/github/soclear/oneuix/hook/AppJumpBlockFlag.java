package io.github.soclear.oneuix.hook;

import android.os.Build;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Enables only the Samsung runtime-initialized app-redirect feature flag. */
final class AppJumpBlockFlag {
    private static Field findField(ClassLoader loader) throws ReflectiveOperationException {
        Field field = loader.loadClass("com.samsung.android.rune.CoreRune")
                .getDeclaredField("SUPPORT_APP_JUMP_BLOCK");
        if (field.getType() != boolean.class || !Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException("Unexpected app-redirect flag type");
        }
        field.setAccessible(true);
        return field;
    }

    static boolean isFinal(ClassLoader loader) throws ReflectiveOperationException {
        return Modifier.isFinal(findField(loader).getModifiers());
    }

    static synchronized void enable(ClassLoader loader) throws ReflectiveOperationException {
        Field field = findField(loader);
        // This read also completes class initialization before changing its runtime value.
        if (field.getBoolean(null)) return;
        if (!Modifier.isFinal(field.getModifiers())) {
            field.setBoolean(null, true);
        } else {
            // The ART field layout and call sites were verified on this firmware only.
            // Do not guess offsets or attempt a JNI final-field write (ART rejects it).
            if (Build.VERSION.SDK_INT != 37 || !Build.FINGERPRINT.contains("/F956U1UEU4EZIH:")) {
                throw new UnsupportedOperationException("Final app-redirect flag needs firmware-specific validation");
            }
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            Method offsetOf = Field.class.getDeclaredMethod("getOffset");
            offsetOf.setAccessible(true);
            long offset = ((Number) offsetOf.invoke(field)).longValue();
            if (offset <= 0 || offset > 65535) throw new IllegalStateException("Unexpected field offset");
            unsafeClass.getMethod("putBoolean", Object.class, long.class, boolean.class)
                    .invoke(unsafe, field.getDeclaringClass(), offset, true);
            unsafeClass.getMethod("fullFence").invoke(unsafe);
        }
        if (!field.getBoolean(null)) throw new IllegalStateException("App-redirect flag readback failed");
    }
}
