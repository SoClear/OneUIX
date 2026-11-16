package dev.colormotion;

/** Suppresses duplicate native/custom submissions within one frame, never a later native update. */
final class BlurFrameGate {
    private Object owner;
    private float value = Float.NaN;
    private long frame = Long.MIN_VALUE;

    void beginFrame(long frameTimeNanos) {
        if (frame != frameTimeNanos) reset();
        frame = frameTimeNanos;
    }

    boolean shouldApply(Object controller, float next) {
        if (owner == controller && value == next) return false;
        owner = controller;
        value = next;
        return true;
    }

    void reset() {
        owner = null;
        value = Float.NaN;
        frame = Long.MIN_VALUE;
    }
}
