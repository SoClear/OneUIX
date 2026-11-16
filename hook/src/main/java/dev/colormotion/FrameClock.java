package dev.colormotion;

/** Accumulates fractional, duration-scaled milliseconds without advancing across an idle reset. */
final class FrameClock {
    static final long DISABLED_ANIMATION_MS = 100_000L;
    private boolean started;
    private long lastFrameNanos;
    // Pending animation time, already scaled. A scale change only affects its new interval.
    private double remainderNanos;

    boolean isStarted() { return started; }

    void reset() {
        started = false;
        remainderNanos = 0;
    }

    void start(long frameTimeNanos) {
        reset();
        started = true;
        lastFrameNanos = frameTimeNanos;
    }

    /** First call establishes the timestamp; zero duration scale completes model animations. */
    long advance(long frameTimeNanos, float durationScale) {
        long elapsed = started ? frameTimeNanos - lastFrameNanos : 0L;
        if (!started) start(frameTimeNanos);
        else lastFrameNanos = frameTimeNanos;
        if (!(durationScale > 0f) || !Float.isFinite(durationScale)) {
            remainderNanos = 0;
            return DISABLED_ANIMATION_MS;
        }
        if (elapsed < 0L) {
            // A new timestamp domain needs a fresh baseline, just like explicit readiness.
            remainderNanos = 0;
            return 0L;
        }
        double nanos = elapsed / (double) durationScale + remainderNanos;
        if (nanos / 1_000_000.0 >= Long.MAX_VALUE) {
            remainderNanos = 0;
            return Long.MAX_VALUE;
        }
        long millis = (long) (nanos / 1_000_000.0);
        remainderNanos = nanos - millis * 1_000_000.0;
        // Division by scales such as 10 can leave an integer boundary a few double
        // ulps short. Normalize within one picosecond, below timestamp resolution.
        if (1_000_000.0 - remainderNanos <= 0.001) {
            millis++;
            remainderNanos = 0;
        }
        return millis;
    }
}
