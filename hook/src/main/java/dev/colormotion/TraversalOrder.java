package dev.colormotion;

import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/**
 * Draws the notification shade window last within a motion frame.
 *
 * Every SystemUI window shares one RenderThread, and each window's draw blocks the UI thread
 * until RenderThread can sync it. The shade's frame takes several milliseconds there. While
 * the shade opens, Samsung also fades the status bar icons, and that small window was drawn
 * after the shade: the UI thread waited for the whole shade frame before it could finish.
 * Moving the shade's already scheduled traversal to the end of the traversal queue lets the
 * small windows sync first. The same draws happen in the same vsync; only their order changes.
 */
final class TraversalOrder {
    private static final int CALLBACK_INSETS_ANIMATION = 2, CALLBACK_TRAVERSAL = 3;
    private final Method post, remove, viewRoot;
    private final Field scheduled, runnable, choreographer;
    private final Runnable reorder = this::reorder;
    private Runnable pending;
    private final Runnable deferred = () -> {
        Runnable task = pending;
        pending = null;
        if (task != null) task.run();
    };
    private View host;
    private boolean queued, disabled;

    private TraversalOrder() throws ReflectiveOperationException {
        post = Choreographer.class.getMethod("postCallback", int.class, Runnable.class, Object.class);
        remove = Choreographer.class.getMethod("removeCallbacks", int.class, Runnable.class, Object.class);
        viewRoot = View.class.getMethod("getViewRootImpl");
        Class<?> root = viewRoot.getReturnType();
        scheduled = root.getDeclaredField("mTraversalScheduled");
        runnable = root.getDeclaredField("mTraversalRunnable");
        choreographer = root.getDeclaredField("mChoreographer");
        if (scheduled.getType() != boolean.class || !Runnable.class.isAssignableFrom(runnable.getType())
                || choreographer.getType() != Choreographer.class) throw new NoSuchFieldException("ViewRootImpl ABI");
        for (Field f : new Field[] {scheduled, runnable, choreographer}) f.setAccessible(true);
        for (Method m : new Method[] {post, remove, viewRoot}) m.setAccessible(true);
    }

    static TraversalOrder create() {
        try { return new TraversalOrder(); }
        catch (Throwable unavailable) {
            XposedBridge.log(Entry.TAG + ": traversal ordering unavailable: " + unavailable);
            return null;
        }
    }

    /** Call from a frame's animation phase; runs after all animation callbacks of that frame. */
    void drawLast(View window) {
        if (disabled || queued || window == null) return;
        try {
            post.invoke(Choreographer.getInstance(), CALLBACK_INSETS_ANIMATION, reorder, null);
            host = window;
            queued = true;
        } catch (Throwable failure) { disable(failure); }
    }

    private void reorder() {
        queued = false;
        View window = host;
        host = null;
        if (disabled || window == null) return;
        try {
            Object root = viewRoot.invoke(window);
            if (root == null || !scheduled.getBoolean(root)) return;
            Object task = runnable.get(root);
            Object owner = choreographer.get(root);
            if (task == null || owner == null) return;
            // Post first: if removal then fails, both run and ViewRootImpl ignores the second
            // (doTraversal checks mTraversalScheduled). A traversal can never be lost.
            pending = (Runnable) task;
            post.invoke(owner, CALLBACK_TRAVERSAL, deferred, null);
            remove.invoke(owner, CALLBACK_TRAVERSAL, task, null);
        } catch (Throwable failure) { disable(failure); }
    }

    private void disable(Throwable failure) {
        disabled = true;
        host = null;
        XposedBridge.log(Entry.TAG + ": traversal ordering disabled: " + failure);
    }
}
