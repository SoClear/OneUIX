package dev.colormotion;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import de.robv.android.xposed.XposedHelpers;

/** Match the native composable ABI and animate main grid cells, not nested/editable child tiles. */
final class QsItemBinding {
    final Method editableItem, header;
    private final Class<?> cellData;
    private final Method foldIn;
    private final Object containsCell;

    QsItemBinding(ClassLoader cl) throws ReflectiveOperationException {
        Class<?> modifier = XposedHelpers.findClass("androidx.compose.ui.Modifier", cl);
        Class<?> function2 = XposedHelpers.findClass("kotlin.jvm.functions.Function2", cl);
        Class<?> composer = XposedHelpers.findClass("androidx.compose.runtime.Composer", cl);
        cellData = XposedHelpers.findClass(
                "com.android.systemui.samsung.quicksetting.ui.base.spannablegrid.SpannableGridCellData", cl);
        editableItem = findEditableItem(XposedHelpers.findClass(
                "com.android.systemui.samsung.quicksetting.ui.base.editable.EditableItemKt", cl), modifier, composer);
        header = findHeader(XposedHelpers.findClass(
                "com.android.systemui.samsung.quicksetting.ui.panel.SecQuickSettingHeaderKt", cl), modifier, composer);
        foldIn = modifier.getMethod("foldIn", Object.class, function2);
        containsCell = Proxy.newProxyInstance(cl, new Class<?>[] {function2}, (proxy, method, args) -> {
            if (method.getName().equals("invoke"))
                return Boolean.TRUE.equals(args[0]) || cellData.isInstance(args[1]);
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return "ColorMotionGridCellScope";
        });
    }

    static Method findEditableItem(Class<?> owner, Class<?> modifier, Class<?> composer)
            throws NoSuchMethodException {
        Method selected = null;
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().startsWith("EditableItem-") || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class || p.length < 8
                    || p[0] != modifier || p[1] != String.class || p[2] != long.class || p[3] != long.class
                    || p[p.length - 4] != composer || p[p.length - 3] != int.class
                    || p[p.length - 2] != int.class || p[p.length - 1] != int.class) continue;
            if (selected != null) throw new NoSuchMethodException("Ambiguous QS item ABI");
            selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("Supported QS item ABI not found");
        return selected;
    }

    static Method findHeader(Class<?> owner, Class<?> modifier, Class<?> composer) throws NoSuchMethodException {
        Method selected = null;
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("SecQuickSettingHeader") || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class || (p.length != 5 && p.length != 6)
                    || p[1] != modifier || p[3] != composer || p[4] != int.class
                    || (p.length == 6 && p[5] != int.class)) continue;
            if (selected != null) throw new NoSuchMethodException("Ambiguous QS header ABI");
            selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("Supported QS header ABI not found");
        return selected;
    }

    Object headerBase(Object[] args, Object companion) {
        boolean defaultModifier = header.getParameterCount() == 6 && ((Integer) args[5] & 1) != 0;
        return defaultModifier || args[1] == null ? companion : args[1];
    }

    void replaceHeader(Object[] args, Object decorated) {
        args[1] = decorated;
        if (header.getParameterCount() == 6) args[5] = (Integer) args[5] & ~1;
    }

    boolean isMainGrid(Object modifier, long offset, long span) throws ReflectiveOperationException {
        if (modifier == null || (int) offset < 0 || (int) span <= 0 || (int) (span >>> 32) <= 0) return false;
        if (cellData.isInstance(modifier)) return true;
        // One UI 9 adds a graphics layer after parent data. This traversal is composition-only,
        // not a global modifier/render hook and not part of the per-frame animation path.
        return Boolean.TRUE.equals(foldIn.invoke(modifier, Boolean.FALSE, containsCell));
    }
}
