package dev.colormotion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The small public height setter is inlined by ART; its large Runnable remains hookable. */
final class PanelHeightBinding {
    final Field panel, height;
    final Method run;

    PanelHeightBinding(ClassLoader cl, Class<?> owner) throws ReflectiveOperationException {
        Field selectedPanel = null, selectedHeight = null;
        Method selected = null;
        for (int suffix : new int[] {13, 14}) {
            Class<?> callback;
            try { callback = Class.forName(owner.getName() + "$$ExternalSyntheticLambda" + suffix, false, cl); }
            catch (ClassNotFoundException missing) { continue; }
            if (!Runnable.class.isAssignableFrom(callback)) continue;
            Field p, h;
            try { p = callback.getField("f$0"); h = callback.getField("f$1"); }
            catch (NoSuchFieldException otherCallback) { continue; }
            if (p.getType() != owner || h.getType() != float.class || callback.getDeclaredFields().length != 2) continue;
            if (selected != null) throw new NoSuchMethodException("Ambiguous panel height callback");
            selected = callback.getMethod("run"); selectedPanel = p; selectedHeight = h;
        }
        if (selected == null) throw new NoSuchMethodException("Unsupported panel height callback");
        run = selected; panel = selectedPanel; height = selectedHeight;
        panel.setAccessible(true); height.setAccessible(true);
    }
}
