package dev.colormotion;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Removes motion viewports within the QS host, preserving individual controls' shaped masks. */
final class QsClipping {
    private static final String ROOT = "com.android.systemui.samsung.quicksetting.view.SecQSPanelComposeRoot";
    private static final String UI = "com.android.systemui.samsung.quicksetting.ui.";
    private final ClassLoader cl;
    private final HookSet hooks;
    private final ArrayList<WeakReference<ViewGroup>> hosts = new ArrayList<>();
    private WeakReference<View> hostRoot = new WeakReference<>(null);
    private boolean active = true, prepared, discoveryDirty = true, awaitingLayout;
    private final ArrayList<WeakReference<View>> watched = new ArrayList<>();
    private ViewTreeObserver discoveryObserver;
    private ViewTreeObserver.OnGlobalLayoutListener discoveryLayout;
    private long discoveryGeneration;
    private Attachment attachment;
    private final class Attachment implements View.OnAttachStateChangeListener {
        private boolean owns(View view) {
            if (attachment != this || !active) return false;
            if (view == hostRoot.get()) return true;
            for (WeakReference<View> reference : watched) if (view == reference.get()) return true;
            return false;
        }
        @Override public void onViewAttachedToWindow(View view) {
            try {
                if (!owns(view)) return;
                invalidateHosts();
                awaitingLayout = true;
                // ViewGroup attaches itself before its children. Discover after the resulting
                // layout, rather than caching a partially attached tree from this callback.
                observeDiscovery();
            } catch (Throwable failure) { lifecycleFailed(failure); }
        }
        @Override public void onViewDetachedFromWindow(View view) {
            try {
                if (!owns(view)) return;
                invalidateHosts();
                awaitingLayout = true;
                if (view == hostRoot.get()) stopObservingDiscovery();
                else observeDiscovery();
                // Android detaches before removing the child from its parent's array. Never
                // rediscover synchronously here: that would cache the disappearing subtree.
            } catch (Throwable failure) { lifecycleFailed(failure); }
        }
    }

    QsClipping(ClassLoader cl) {
        this(cl, new HookSet());
    }

    QsClipping(ClassLoader cl, HookSet hooks) {
        this.cl = cl;
        this.hooks = hooks;
    }

    void install() throws Throwable {
        Class<?> modifier = XposedHelpers.findClass("androidx.compose.ui.Modifier", cl);
        Class<?> shape = XposedHelpers.findClass("androidx.compose.ui.graphics.Shape", cl);
        // ClipKt helpers are AOT-inlined. Their large default graphicsLayer factory is not.
        // Adjust the viewport modifier when composed, instead of hooking every rendering backend
        // for every animated layer throughout SystemUI. This also survives modifier updates.
        hooks.findAndHookMethod("androidx.compose.ui.graphics.GraphicsLayerModifierKt", cl,
                "graphicsLayer-_6ThJ44$default", modifier, float.class, float.class, float.class,
                float.class, float.class, float.class, float.class, shape, boolean.class,
                int.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        int defaults = (Integer) param.args[11];
                        boolean clip = (defaults & 4096) == 0 && Boolean.TRUE.equals(param.args[9]);
                        if (!clip) return;
                        Object suppliedShape = param.args[8];
                        String shapeName = (defaults & 2048) != 0 || suppliedShape == null
                                ? "androidx.compose.ui.graphics.RectangleShapeKt$RectangleShape$1"
                                : suppliedShape.getClass().getName();
                        // Rounded tile/control masks can never match this policy. Avoid walking
                        // the UI thread's stack for each one during a cold scene composition.
                        if (!isViewportShape(shapeName)) return;
                        String caller = nearestQsCaller();
                        adjustViewport(param.args, shapeName, caller);
                    }
                });
        XposedBridge.log(Entry.TAG + ": QS viewport clips removed during composition (caller lookup: "
                + (fastCallerLookup() ? "frame classes" : "stack trace") + ")");
    }

    // The tile box's rounded/bottom-cut masks also hide off-page tiles. Removing them
    // exposes extra rows over the brightness/volume controls, especially on stock One UI 9.
    // The outer grid item moves as one layer, so its internal scrolling masks stay native.
    static boolean adjustViewport(Object[] args, String shapeName, String caller) {
        int defaults = (Integer) args[11];
        if ((defaults & 4096) != 0 || !Boolean.TRUE.equals(args[9]) || caller == null
                || !(caller.startsWith(UI + "panel.") || caller.startsWith(UI + "base.spannablegrid."))
                || !isViewportShape(shapeName)) return false;
        args[9] = false;
        // Auto alpha can still bound descendants to an intermediate texture. Change only
        // these viewport layers; controls and explicit Offscreen retain native compositing.
        if ((defaults & 65536) != 0 || (Integer) args[10] == 0) {
            args[10] = 2; // CompositingStrategy.ModulateAlpha
            args[11] = defaults & ~65536;
        }
        return true;
    }

    static boolean isViewportShape(String shapeClass) {
        return shapeClass.equals("androidx.compose.foundation.VerticalScrollableClipShape")
                || shapeClass.equals("androidx.compose.foundation.HorizontalScrollableClipShape")
                || shapeClass.equals("androidx.compose.ui.graphics.RectangleShapeKt$RectangleShape$1");
    }

    /**
     * Modifier.alpha() and clipToBounds() also reach this hook with a rectangular clip, so it
     * runs for most QS tiles on every composition. getStackTrace() builds a StackTraceElement
     * per frame and decodes each method's line table from SystemUI's dex; after the phone idles
     * those pages are evicted, and the first QS/shade composition stalls on disk reads.
     * ART's internal trace already holds every frame's declaring class, in the same order.
     */
    private static final java.lang.reflect.Field BACKTRACE = backtraceField();
    static final String UNRESOLVED = new String("unresolved");

    private static java.lang.reflect.Field backtraceField() {
        try {
            java.lang.reflect.Field field = Throwable.class.getDeclaredField("backtrace");
            field.setAccessible(true);
            return field;
        } catch (Throwable unavailable) { return null; }
    }

    private static boolean fastCallerLookup() {
        try {
            Object trace = BACKTRACE == null ? null : BACKTRACE.get(new Throwable());
            return trace instanceof Object[] && nearestQsCaller((Object[]) trace) != UNRESOLVED;
        } catch (Throwable unavailable) { return false; }
    }

    private static String nearestQsCaller() {
        Throwable here = new Throwable();
        if (BACKTRACE != null) {
            try {
                Object trace = BACKTRACE.get(here);
                if (trace instanceof Object[]) {
                    String caller = nearestQsCaller((Object[]) trace);
                    if (caller != UNRESOLVED) return caller;
                }
            } catch (Throwable ignored) { }
        }
        return nearestQsCaller(here.getStackTrace());
    }

    /** ART layout: [0] method/dex-pc pointer array (2 slots per frame), then each frame's class. */
    static String nearestQsCaller(Object[] trace) {
        int depth = trace.length - 1;
        if (depth < 1) return UNRESOLVED;
        Object pointers = trace[0];
        int slots = pointers instanceof long[] ? ((long[]) pointers).length
                : pointers instanceof int[] ? ((int[]) pointers).length : -1;
        if (slots != depth * 2) return UNRESOLVED;
        for (int i = 1; i <= depth; i++) {
            // A copied default method stores its class loader here, and its reported frame
            // class is the interface. Use the exact element path for such stacks.
            if (!(trace[i] instanceof Class)) return UNRESOLVED;
            String name = ((Class<?>) trace[i]).getName();
            if (name.startsWith(UI)) return name;
        }
        return null;
    }

    static String nearestQsCaller(StackTraceElement[] frames) {
        for (StackTraceElement frame : frames) {
            String name = frame.getClassName();
            if (name.startsWith(UI)) return name;
        }
        return null;
    }

    /** View wrappers can still clip the entire Compose result, independently of Compose layers. */
    void prepareHosts(View qs) {
        if (!active) return;
        if (hostRoot.get() != qs) {
            clearHost();
            if (qs == null) return;
            hostRoot = new WeakReference<>(qs);
            attachment = new Attachment();
            qs.addOnAttachStateChangeListener(attachment);
        }
        if (qs == null || prepared) return;
        // A detached tree can be rewritten without detach callbacks. Do not certify its cache.
        // Missing discovery sleeps until an actual layout/attachment signal, not every frame.
        if (awaitingLayout || !qs.isAttachedToWindow() || !qs.isShown()) { observeDiscovery(); return; }
        if (discoveryDirty) discoverHosts(qs);
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active) clearHost();
    }

    private void discoverHosts(View qs) {
        discoveryDirty = false;
        View root = findRoot(qs, 0);
        if (root == null) { observeDiscovery(); return; }
        collectHosts(root);
        View current = root;
        while (current != qs) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof ViewGroup)) break;
            ViewGroup group = (ViewGroup) parent;
            hosts.add(new WeakReference<>(group));
            current = group;
        }
        for (WeakReference<ViewGroup> reference : hosts) {
            ViewGroup host = reference.get();
            if (host == null) continue;
            if (host.getClipChildren()) host.setClipChildren(false);
            if (host.getClipToPadding()) host.setClipToPadding(false);
            if (host.getClipBounds() != null) host.setClipBounds(null);
            if (host.getClipToOutline()) host.setClipToOutline(false);
            if (host != qs) {
                host.addOnAttachStateChangeListener(attachment);
                watched.add(new WeakReference<>(host));
            }
        }
        prepared = true;
        stopObservingDiscovery();
    }

    private void invalidateHosts() {
        prepared = false;
        discoveryDirty = true;
        hosts.clear();
        for (WeakReference<View> reference : watched) {
            View view = reference.get();
            if (view != null) view.removeOnAttachStateChangeListener(attachment);
        }
        watched.clear();
    }

    /** Only unresolved discovery observes global layout; the successful cache has no global hook. */
    private void observeDiscovery() {
        View qs = hostRoot.get();
        if (!active || qs == null || !qs.isAttachedToWindow()) return;
        ViewTreeObserver observer = qs.getViewTreeObserver();
        if (discoveryObserver == observer && observer.isAlive()) return;
        stopObservingDiscovery();
        if (!observer.isAlive()) return;
        long generation = discoveryGeneration;
        discoveryObserver = observer;
        discoveryLayout = () -> {
            try {
                if (generation != discoveryGeneration || !active || qs != hostRoot.get()
                        || !qs.isAttachedToWindow() || !qs.isShown()) return;
                // A failed scan is retried only after real layout/content availability changes.
                awaitingLayout = false;
                discoverHosts(qs);
            } catch (Throwable failure) { lifecycleFailed(failure); }
        };
        observer.addOnGlobalLayoutListener(discoveryLayout);
    }

    private void stopObservingDiscovery() {
        discoveryGeneration++;
        if (discoveryLayout != null) {
            if (discoveryObserver != null && discoveryObserver.isAlive())
                discoveryObserver.removeOnGlobalLayoutListener(discoveryLayout);
            // Attachment merges a floating observer before attach listeners run. Its listener
            // may now belong to the current live tree even when the stored observer is dead.
            View qs = hostRoot.get();
            if (qs != null) {
                ViewTreeObserver current = qs.getViewTreeObserver();
                if (current != discoveryObserver && current.isAlive())
                    current.removeOnGlobalLayoutListener(discoveryLayout);
            }
        }
        discoveryObserver = null;
        discoveryLayout = null;
    }

    private void clearHost() {
        stopObservingDiscovery();
        invalidateHosts();
        View qs = hostRoot.get();
        if (qs != null) qs.removeOnAttachStateChangeListener(attachment);
        attachment = null;
        awaitingLayout = false;
        hostRoot = new WeakReference<>(null);
    }

    private void lifecycleFailed(Throwable failure) {
        clearHost();
        XposedBridge.log(Entry.TAG + ": QS clipping lifecycle: " + failure);
    }

    private static View findRoot(View v, int depth) {
        if (ROOT.equals(v.getClass().getName())) return v;
        if (!(v instanceof ViewGroup) || depth >= 4) return null;
        ViewGroup group = (ViewGroup) v;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findRoot(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private void collectHosts(View v) {
        if (!(v instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) v;
        hosts.add(new WeakReference<>(group));
        // Stop here: embedded native controls below AndroidViewsHandler keep their own masks.
        if (v.getClass().getName().equals("androidx.compose.ui.platform.AndroidComposeView")) return;
        for (int i = 0; i < group.getChildCount(); i++) collectHosts(group.getChildAt(i));
    }

}
