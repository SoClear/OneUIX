package dev.colormotion;

import android.animation.ValueAnimator;
import android.view.Choreographer;

/** Runs a {@link Spring} on the Choreographer, like DynamicAnimation.doAnimationFrame. */
final class SpringDriver implements Choreographer.FrameCallback {
    interface Listener {
        void onUpdate(Spring spring);

        default void onEnd(Spring spring) {}
    }

    final Spring spring = new Spring();
    private final Listener listener;
    private boolean running;
    private final FrameClock clock = new FrameClock();
    private Run callback;

    /** A cancelled callback may already be in Choreographer's current dispatch snapshot. */
    private static final class Run implements Choreographer.FrameCallback {
        private final SpringDriver driver;
        Run(SpringDriver driver) { this.driver = driver; }
        @Override public void doFrame(long frameTimeNanos) {
            if (driver.callback == this) driver.doFrame(frameTimeNanos);
        }
    }

    SpringDriver(Listener listener) {
        this.listener = listener;
    }

    boolean isRunning() {
        return running;
    }

    /** Same semantics as SpringAnimation.animateToFinalPosition: keeps current value and velocity. */
    void animateTo(float target) {
        spring.target = target;
        if (!running) {
            if (spring.isAtRest() && spring.value == target) {
                return;
            }
            running = true;
            callback = new Run(this);
            clock.reset();
            Choreographer.getInstance().postFrameCallback(callback);
        }
    }

    void cancel() {
        if (running) {
            running = false;
            Choreographer.getInstance().removeFrameCallback(callback);
            callback = null;
            clock.reset();
        }
    }

    void snapTo(float v) {
        cancel();
        spring.snapTo(v);
        listener.onUpdate(spring);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (!clock.isStarted()) {
            clock.start(frameTimeNanos);
            Choreographer.getInstance().postFrameCallback(callback);
            return;
        }
        float scale = ValueAnimator.getDurationScale();
        long dtMs = clock.advance(frameTimeNanos, scale);
        boolean done;
        if (scale == 0f) {
            spring.snapTo(spring.target);
            done = true;
        } else {
            done = spring.step(dtMs);
        }
        Run currentCallback = callback;
        float completedTarget = spring.target;
        listener.onUpdate(spring);
        // The update may fail open, cancel, or start a fresh run. That run owns its
        // already queued callback and timestamp; this frame must not repost or end it.
        if (!running || callback != currentCallback) return;
        if (done && Float.floatToIntBits(spring.target) != Float.floatToIntBits(completedTarget)) {
            done = false; // A settlement listener can retarget the still-running spring.
        }
        if (done) {
            running = false;
            callback = null;
            clock.reset();
            listener.onEnd(spring);
        } else {
            Choreographer.getInstance().postFrameCallback(callback);
        }
    }
}
