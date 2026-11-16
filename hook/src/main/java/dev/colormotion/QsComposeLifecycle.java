package dev.colormotion;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Retires only the detached child observed during this controller's last native creation.
 *
 * Stock retains child-owned repeat listeners across controller attachments. UI9 disposes
 * content but retains wrappers. Native creation/configuration/flows must still run afresh.
 * This optional optimization does not recover children created before its observation, nor
 * certify ownership against arbitrary third-party suppression/substitution of native calls.
 */
final class QsComposeLifecycle extends XC_MethodHook {
    private static final String CONTROLLER = "com.android.systemui.samsung.quicksetting.SecQSPanelCompose";
    private static final String ROOT = "com.android.systemui.samsung.quicksetting.view.SecQSPanelComposeRoot";
    private static final String CALL = "dev.colormotion.QsComposeLifecycle.call";
    private static final int MAX_OWNERS = 32, MAX_CHILDREN = 256, MAX_LOGS = 4;

    private final Class<?> controllerType, rootType, composeType;
    private final Field rootField, composeField;
    private final Method dispose;
    private final ArrayList<Owner> owners = new ArrayList<>();
    private boolean busy, reentered;
    private int failures;

    /** No controller, root or view is strongly retained between calls. Identity, not equals. */
    private static final class Owner {
        final WeakReference<Object> controller;
        final WeakReference<ViewGroup> root;
        final WeakReference<View> view;
        Owner(Object controller, ViewGroup root, View view) {
            this.controller = new WeakReference<>(controller);
            this.root = new WeakReference<>(root);
            this.view = new WeakReference<>(view);
        }
    }

    /** Strong references exist only in the synchronous before/native/after invocation. */
    private static final class Call {
        Object controller;
        ViewGroup root;
        View[] children;
    }

    private QsComposeLifecycle(ClassLoader cl) throws ReflectiveOperationException {
        controllerType = XposedHelpers.findClass(CONTROLLER, cl);
        rootType = XposedHelpers.findClass(ROOT, cl);
        composeType = XposedHelpers.findClass("androidx.compose.ui.platform.ComposeView", cl);
        Class<?> base = XposedHelpers.findClass("com.android.systemui.util.ViewController", cl);
        Class<?> abstractCompose = XposedHelpers.findClass("androidx.compose.ui.platform.AbstractComposeView", cl);
        require(Modifier.isFinal(controllerType.getModifiers()) && controllerType.getSuperclass() == base);
        require(ViewGroup.class.isAssignableFrom(rootType) && ViewGroup.class.isAssignableFrom(composeType)
                && composeType.getSuperclass() == abstractCompose);
        rootField = base.getDeclaredField("mView");
        require(rootField.getType() == View.class && !Modifier.isStatic(rootField.getModifiers()));
        rootField.setAccessible(true);
        Field optional;
        try {
            optional = controllerType.getDeclaredField("composeView");
            require(optional.getType() == composeType && !Modifier.isStatic(optional.getModifiers()));
            optional.setAccessible(true);
        } catch (NoSuchFieldException stock) { optional = null; }
        composeField = optional;
        dispose = abstractCompose.getDeclaredMethod("disposeComposition");
        requireVoidInstance(dispose);
    }

    /** Binding precedes registration; the sole registered hook belongs to the caller's HookSet. */
    static void installOptional(ClassLoader cl, HookSet hooks) {
        try {
            QsComposeLifecycle callback = new QsComposeLifecycle(cl);
            Method attach = callback.controllerType.getDeclaredMethod("attachComposeView");
            requireVoidInstance(attach);
            int count = 0;
            for (Method method : callback.controllerType.getDeclaredMethods()) {
                if (method.getName().equals("attachComposeView")) count++;
            }
            require(count == 1);
            // Do not put fallible binding/logging after this registration, or install a
            // private HookSet that the containing QS pipeline cannot roll back.
            hooks.method(attach, callback);
        } catch (Throwable failure) {
            try { XposedBridge.log("ColorMotion: optional QS Compose lifecycle unavailable: " + failure); }
            catch (Throwable ignored) {}
        }
    }

    private static void require(boolean known) throws NoSuchMethodException {
        if (!known) throw new NoSuchMethodException("Unsupported native QS Compose lifecycle shape");
    }

    private static void requireVoidInstance(Method method) throws NoSuchMethodException {
        int flags = method.getModifiers();
        require(method.getReturnType() == void.class && method.getParameterCount() == 0
                && Modifier.isPublic(flags) && Modifier.isFinal(flags) && !Modifier.isStatic(flags));
    }

    @Override protected synchronized void beforeHookedMethod(MethodHookParam param) {
        try {
            if (param.thisObject == null || param.thisObject.getClass() != controllerType) return;
            if (busy) {
                // Disposal/removal can invoke external callbacks. Nested native calls still
                // run, but neither their child nor the outer call's child is safe to claim.
                reentered = true;
                owners.clear();
                return;
            }
            Call call = new Call();
            param.setObjectExtra(CALL, call);
            busy = true;
            reentered = false;
            call.controller = param.thisObject;
            begin(call);
        } catch (Throwable failure) { owners.clear(); failed(failure); }
    }

    private void begin(Call call) throws Throwable {
        Object value = rootField.get(call.controller);
        ViewGroup root = value != null && value.getClass() == rootType ? (ViewGroup) value : null;
        Owner previous = null;
        for (int i = owners.size() - 1; i >= 0; i--) {
            Owner owner = owners.get(i);
            Object controller = owner.controller.get();
            ViewGroup oldRoot = owner.root.get();
            View old = owner.view.get();
            if (controller == null || oldRoot == null || old == null
                    || controller == call.controller || oldRoot == root) {
                owners.remove(i);
                if (controller == call.controller && oldRoot == root && old != null) previous = owner;
                // A different controller using this root invalidates its former claim.
            }
        }
        if (root == null || !root.isAttachedToWindow()) return;
        if (previous != null) {
            View old = previous.view.get();
            if (old == null || old.getClass() != composeType || old.getParent() != root
                    || old.isAttachedToWindow() || root.getLayoutTransition() != null
                    || old.getAnimation() != null) return;
            // Forget the claim before cleanup. A failure must never retry a partially
            // disposed/removed child on a future attachment.
            dispose.invoke(old);
            if (reentered || !root.isAttachedToWindow() || rootField.get(call.controller) != root || old.getParent() != root
                    || old.isAttachedToWindow() || root.getLayoutTransition() != null
                    || old.getAnimation() != null) return;
            root.removeView(old);
            if (reentered || !root.isAttachedToWindow() || old.getParent() != null || rootField.get(call.controller) != root) return;
        }
        if (reentered) return;
        View[] children = snapshot(root);
        call.root = root;
        call.children = children;
    }

    private static View[] snapshot(ViewGroup root) {
        int count = root.getChildCount();
        if (count < 0 || count > MAX_CHILDREN) throw new IllegalStateException("Unexpected QS child count");
        View[] result = new View[count];
        for (int i = 0; i < count; i++) {
            View view = root.getChildAt(i);
            if (view == null || view.getParent() != root) throw new IllegalStateException("Unstable QS children");
            result[i] = view;
        }
        if (root.getChildCount() != count) throw new IllegalStateException("QS children changed during snapshot");
        return result;
    }

    @Override protected synchronized void afterHookedMethod(MethodHookParam param) {
        Call call = null;
        try {
            call = (Call) param.getObjectExtra(CALL);
            if (call == null) return;
            param.setObjectExtra(CALL, null);
            if (param.hasThrowable() || reentered || call.root == null || call.children == null || !call.root.isAttachedToWindow()
                    || param.thisObject != call.controller || rootField.get(call.controller) != call.root) return;
            View candidate = null;
            for (View view : snapshot(call.root)) {
                boolean existed = false;
                for (View old : call.children) if (old == view) { existed = true; break; }
                if (existed) continue;
                if (candidate != null || view.getClass() != composeType) return;
                candidate = view;
            }
            if (candidate == null || (composeField != null && composeField.get(call.controller) != candidate)) return;
            if (owners.size() == MAX_OWNERS) owners.remove(0);
            owners.add(new Owner(call.controller, call.root, candidate));
        } catch (Throwable failure) { owners.clear(); failed(failure); }
        finally {
            if (call != null) {
                call.controller = null;
                call.root = null;
                call.children = null;
                busy = reentered = false;
            }
        }
    }

    private void failed(Throwable failure) {
        if (failures < MAX_LOGS) {
            failures++;
            try { XposedBridge.log("ColorMotion: QS Compose lifecycle skipped: " + failure); }
            catch (Throwable ignored) {}
        }
    }
}
