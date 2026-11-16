package dev.colormotion;

/**
 * Port of Facebook Rebound's Spring as stepped by the reference
 * AndroidSpringLooperFactory$ChoreographerAndroidSpringLooper: RK4 at a fixed 1 ms step, frame delta
 * capped at 64 ms, interpolation of the leftover time, Origami tension/friction conversion from
 * SpringConfig.fromOrigamiTensionAndFriction.
 */
final class ReboundSpring {
    private static final double STEP = 0.001;
    private static final double MAX_DELTA = 0.064;

    double tension;
    double friction;
    double position;
    double velocity;
    double endValue;
    double restSpeed = 0.005;
    double restDisplacement = 0.005;

    private double tempPosition;
    private double tempVelocity;
    private double prevPosition;
    private double prevVelocity;
    private double accumulator;
    private boolean wasAtRest = true;

    ReboundSpring origami(double tension, double friction) {
        this.tension = tension == 0 ? 0 : (tension - 30.0) * 3.62 + 194.0;
        this.friction = friction == 0 ? 0 : 25.0 + (friction - 8.0) * 3.0;
        return this;
    }

    void setCurrentValue(double v) {
        position = v;
        tempPosition = v;
        velocity = 0;
        tempVelocity = 0;
        accumulator = 0;
    }

    void setEndValue(double v) {
        if (endValue != v || !isAtRest()) {
            endValue = v;
            wasAtRest = false;
        }
    }

    boolean isAtRest() {
        return Math.abs(velocity) <= restSpeed
                && (Math.abs(endValue - position) <= restDisplacement || tension == 0);
    }

    /** Returns true while the spring still needs frames. */
    boolean advance(double deltaSec) {
        boolean atRest = isAtRest();
        if (atRest && wasAtRest) {
            return false;
        }
        accumulator += Math.min(deltaSec, MAX_DELTA);
        double k = tension, c = friction;
        double pos = position, vel = velocity, tPos = tempPosition, tVel = tempVelocity;
        while (accumulator >= STEP) {
            accumulator -= STEP;
            if (accumulator < STEP) {
                prevPosition = pos;
                prevVelocity = vel;
            }
            double end = endValue;
            // Rebound evaluates the first acceleration from the previous step's temp position.
            double aAcc = (end - tPos) * k - c * vel;
            double bPos = pos + vel * STEP * 0.5;
            double bVel = vel + aAcc * STEP * 0.5;
            double bAcc = (end - bPos) * k - c * bVel;
            double cPos = pos + bVel * STEP * 0.5;
            double cVel = vel + bAcc * STEP * 0.5;
            double cAcc = (end - cPos) * k - c * cVel;
            double dPos = pos + cVel * STEP;
            double dVel = vel + cAcc * STEP;
            double dAcc = (end - dPos) * k - c * dVel;
            pos = pos + (vel + 2.0 * (bVel + cVel) + dVel) / 6.0 * STEP;
            vel = vel + (aAcc + 2.0 * (bAcc + cAcc) + dAcc) / 6.0 * STEP;
            tPos = dPos;
            tVel = dVel;
        }
        tempPosition = tPos;
        tempVelocity = tVel;
        position = pos;
        velocity = vel;
        if (accumulator > 0) {
            double a = accumulator / STEP;
            position = prevPosition * (1 - a) + pos * a;
            velocity = prevVelocity * (1 - a) + vel * a;
        }
        if (isAtRest()) {
            if (k > 0) {
                position = endValue;
            } else {
                endValue = position;
            }
            velocity = 0;
            atRest = true;
        }
        wasAtRest = atRest;
        return !atRest;
    }
}
