package dev.colormotion;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** A failed compatibility binding must not leave half an animation pipeline installed. */
final class HookSet {
    private final ArrayList<XC_MethodHook.Unhook> installed = new ArrayList<>();

    XC_MethodHook.Unhook method(Member member, XC_MethodHook callback) {
        XC_MethodHook.Unhook hook = XposedBridge.hookMethod(member, callback);
        if (hook == null) throw new IllegalStateException("Native hook registration failed: " + member);
        installed.add(hook);
        return hook;
    }

    void findAndHookMethod(Class<?> owner, String name, Object... parameters) {
        Class<?>[] types = new Class<?>[parameters.length - 1];
        for (int i = 0; i < types.length; i++) types[i] = (Class<?>) parameters[i];
        method(XposedHelpers.findMethodExact(owner, name, (Object[]) types),
                (XC_MethodHook) parameters[parameters.length - 1]);
    }

    void findAndHookMethod(String owner, ClassLoader loader, String name, Object... parameters) {
        findAndHookMethod(XposedHelpers.findClass(owner, loader), name, parameters);
    }

    Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> owner, String name, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> result = new HashSet<>();
        // Record each handle immediately; a later overload can throw or return null.
        for (Member member : owner.getDeclaredMethods()) {
            if (member.getName().equals(name)) result.add(method(member, callback));
        }
        return result;
    }

    void rollback() {
        for (int i = installed.size() - 1; i >= 0; i--) {
            XC_MethodHook.Unhook hook = installed.get(i);
            try { if (hook != null) hook.unhook(); }
            catch (Throwable failure) { XposedBridge.log("ColorMotion: hook removal failed: " + failure); }
        }
        installed.clear();
    }
}
