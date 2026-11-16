package dev.colormotion;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Cached metadata only. Every capture still reads the live wrapper and calls its actual getter. */
final class SplitViewBinding {
    private static final Object[] NO_ARGS = new Object[0];
    private static final String[] NAMES = {"header", "clockDateContainer", "buttonContainer",
            "blurParent", "largeShadowView", "smallShadowView"};
    final Class<?> owner;
    private final Field[] fields = new Field[NAMES.length];
    private final Class<?>[] getterOwners = new Class<?>[NAMES.length];
    private final Method[] getters = new Method[NAMES.length];

    SplitViewBinding(Class<?> owner) {
        this.owner = owner;
        for (int i = 0; i < fields.length; i++) {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Field f = type.getDeclaredField(NAMES[i]);
                    f.setAccessible(true);
                    fields[i] = f;
                    break;
                } catch (NoSuchFieldException absent) { /* Search the inherited contract. */ }
                catch (Throwable unsupported) { break; }
            }
        }
    }

    View read(Object animator, int slot) {
        try {
            Field field = fields[slot];
            if (field == null) return null;
            Object wrapper = field.get(animator);
            if (wrapper == null) return null;
            Class<?> type = wrapper.getClass();
            if (getterOwners[slot] != type) {
                Method selected = null;
                for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass()) {
                    try {
                        Method method = candidate.getDeclaredMethod("getView");
                        method.setAccessible(true);
                        selected = method;
                        break;
                    } catch (NoSuchMethodException absent) { /* Search the inherited getter. */ }
                }
                // A failed access/linkage operation must not cache an unavailable getter.
                getters[slot] = selected;
                getterOwners[slot] = type;
            }
            Method method = getters[slot];
            return method == null ? null : (View) method.invoke(wrapper, NO_ARGS);
        } catch (Throwable unavailable) { return null; }
    }
}
