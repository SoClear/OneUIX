package dev.colormotion;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.XposedHelpers;

/** The VPN disclosure and mic-mode banner live outside EditableItem on both native layouts. */
final class QsAuxBinding {
    final Method footer, banner;

    QsAuxBinding(ClassLoader cl) throws ReflectiveOperationException {
        Class<?> modifier = XposedHelpers.findClass("androidx.compose.ui.Modifier", cl);
        Class<?> composer = XposedHelpers.findClass("androidx.compose.runtime.Composer", cl);
        String owner = "com.android.systemui.samsung.quicksetting.ui.banner.";
        footer = find(XposedHelpers.findClass(owner + "SecurityFooterKt", cl), "SecurityFooterContent", 0, modifier, composer);
        banner = find(XposedHelpers.findClass(owner + "TopBannerKt", cl), "TopBanner", 1, modifier, composer);
    }

    static Method find(Class<?> owner, String name, int modifierIndex, Class<?> modifier, Class<?> composer)
            throws NoSuchMethodException {
        Method selected = null;
        for (Method m : owner.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class
                    || (p.length != 6 && p.length != 7) || p[modifierIndex] != modifier
                    || p[p.length - 2] != composer || p[p.length - 1] != int.class) continue;
            if (selected != null) throw new NoSuchMethodException("Ambiguous QS auxiliary ABI: " + name);
            selected = m;
        }
        if (selected == null) throw new NoSuchMethodException("Unsupported QS auxiliary ABI: " + name);
        return selected;
    }
}
