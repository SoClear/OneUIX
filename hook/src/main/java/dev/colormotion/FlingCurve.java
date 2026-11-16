package dev.colormotion;

import android.animation.TimeInterpolator;

/**
 * Pre-simulated spring trajectory used to drive Samsung's own fling ValueAnimator, so its
 * listeners and end callbacks keep working while the motion follows the ColorOS spring.
 */
final class FlingCurve implements TimeInterpolator {
    interface ResponseSchedule {
        /** Returns the spring response (seconds) to use at the given fraction. */
        float responseAt(float fraction);
    }

    private static final int MAX_MS = 4000;

    /** One UI-thread workspace; completed curves own copies and survive later simulations. */
    static final class Workspace {
        final float[] positions = new float[MAX_MS + 1];
        final float[] velocities = new float[MAX_MS + 1];
    }

    final long durationMs;
    private final float[] samples;
    private final float start;
    private final float end;
    private final float[] velocities;
    // UI-thread phase of the last successful interpolation, in model milliseconds.
    private float sampledMs;

    private FlingCurve(float[] samples, float[] velocities, float start, float end) {
        this.samples = samples;
        this.velocities = velocities;
        this.start = start;
        this.end = end;
        this.durationMs = samples.length - 1;
    }

    static FlingCurve simulate(float x0, float v0, float target, ResponseSchedule schedule,
            float minVisibleChange) {
        return simulate(x0, v0, target, schedule, minVisibleChange, new Workspace());
    }

    static FlingCurve simulate(float x0, float v0, float target, ResponseSchedule schedule,
            float minVisibleChange, Workspace workspace) {
        Spring s = new Spring().damping(1.0).minimumVisibleChange(minVisibleChange);
        s.value = x0;
        s.velocity = v0;
        s.target = target;
        float[] buf = workspace.positions;
        float[] vel = workspace.velocities;
        buf[0] = x0;
        vel[0] = v0;
        int n = 0;
        while (n < MAX_MS) {
            s.response(schedule.responseAt(s.value));
            boolean done = s.step(1);
            n++;
            buf[n] = s.value;
            vel[n] = s.velocity;
            if (done) {
                break;
            }
        }
        float[] out = new float[n + 1];
        float[] outV = new float[n + 1];
        System.arraycopy(buf, 0, out, 0, n + 1);
        System.arraycopy(vel, 0, outV, 0, n + 1);
        out[n] = target;
        return new FlingCurve(out, outV, x0, target);
    }

    /** Spring value at elapsed milliseconds. */
    float valueAt(long ms) {
        if (ms <= 0) return samples[0];
        if (ms >= durationMs) return samples[samples.length - 1];
        return samples[(int) ms];
    }

    float velocityAt(long ms) {
        if (ms <= 0) return velocities[0];
        if (ms >= durationMs) return 0f;
        return velocities[(int) ms];
    }

    /** Tangent at the last evaluated native pose, in fraction per model second. */
    float velocityAtLastSample() {
        // Equal endpoints always produce the constant interpolation 1, even with initial momentum.
        if (end == start || sampledMs >= durationMs) return 0f;
        if (sampledMs <= 0f) return velocities[0];
        int i = (int) sampledMs;
        float f = sampledMs - i;
        return velocities[i] + (velocities[i + 1] - velocities[i]) * f;
    }

    @Override
    public float getInterpolation(float input) {
        if (end == start) {
            return 1f;
        }
        float pos = input * durationMs;
        int i = (int) pos;
        float x;
        if (i >= samples.length - 1) {
            x = samples[samples.length - 1];
        } else {
            float f = pos - i;
            x = samples[i] + (samples[i + 1] - samples[i]) * f;
        }
        float result = (x - start) / (end - start);
        // Publish only after evaluation succeeds. Keep the interpolation's existing output and
        // failure behavior; NaN is not a usable pose, and endpoint/extrapolated phases are bounded.
        if (!Float.isNaN(result)) sampledMs = Math.max(0f, Math.min(pos, durationMs));
        return result;
    }
}
