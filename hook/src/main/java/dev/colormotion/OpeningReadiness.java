package dev.colormotion;

import android.view.View;
import android.view.ViewTreeObserver;

/** A scoped first-traversal latch. It sleeps without polling and retains no host after release. */
final class OpeningReadiness {
    interface Listener {
        boolean isReady(View host) throws Throwable;
        void onReady();
        void onFailure(Throwable failure);
    }

    private final Listener listener;
    private boolean waiting;
    private View host;
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnPreDrawListener preDraw;
    private long generation;
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {
            if (waiting && view == host) observe();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            try { if (view == host) stopPreDraw(); }
            catch (Throwable failure) { fail(failure); }
        }
    };

    OpeningReadiness(Listener listener) { this.listener = listener; }

    boolean isWaiting() { return waiting; }

    void begin(View next) {
        cancel();
        waiting = true;
        setHost(next);
    }

    void setHost(View next) {
        if (!waiting) return;
        try {
            if (host != next) {
                stopPreDraw();
                if (host != null) host.removeOnAttachStateChangeListener(attachment);
                host = next;
                if (host != null) host.addOnAttachStateChangeListener(attachment);
            }
            observe();
        } catch (Throwable failure) { fail(failure); }
    }

    private void observe() {
        try {
            if (!waiting || host == null) return;
            ViewTreeObserver next = host.getViewTreeObserver();
            if (next == observer && next.isAlive()) return;
            stopPreDraw();
            if (!next.isAlive()) return;
            observer = next;
            long token = generation;
            preDraw = () -> onPreDraw(token);
            observer.addOnPreDrawListener(preDraw);
        } catch (Throwable failure) { fail(failure); }
    }

    private boolean onPreDraw(long token) {
        try {
            if (!waiting || token != generation) return true;
            View view = host;
            if (view == null || !view.isAttachedToWindow() || !view.isShown()
                    || view.isLayoutRequested() || view.getWidth() <= 0 || view.getHeight() <= 0
                    || !listener.isReady(view)) return true;
            cancel();
            listener.onReady();
        } catch (Throwable failure) { fail(failure); }
        return true;
    }

    /** The native children updater may run after our listener in the same pre-draw dispatch. */
    void afterTraversal(View view) {
        if (!waiting || view != host) return;
        try {
            observe();
            if (observer != null && observer == view.getViewTreeObserver() && observer.isAlive())
                onPreDraw(generation);
        } catch (Throwable failure) { fail(failure); }
    }

    private void stopPreDraw() {
        generation++;
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(preDraw);
        // A floating tree's listeners can migrate into a different live observer on attach.
        if (host != null && preDraw != null) {
            ViewTreeObserver current = host.getViewTreeObserver();
            if (current != observer && current.isAlive()) current.removeOnPreDrawListener(preDraw);
        }
        observer = null;
        preDraw = null;
    }

    void cancel() {
        waiting = false;
        stopPreDraw();
        if (host != null) host.removeOnAttachStateChangeListener(attachment);
        host = null;
    }

    private void fail(Throwable failure) {
        try { cancel(); }
        finally { listener.onFailure(failure); }
    }
}
