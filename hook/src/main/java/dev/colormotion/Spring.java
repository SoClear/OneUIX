package dev.colormotion;

/**
 * Damped harmonic oscillator stepped with the closed-form solution.
 *
 * Mirrors androidx.dynamicanimation SpringForce.updateValues and the reference
 * COUISpringForce.updateValues (defpackage/COUISpringForce.java): stiffness = naturalFreq^2,
 * setResponse(r) => naturalFreq = 2*pi/r, setBounce(b) => dampingRatio = 1 - b,
 * valueThreshold = minimumVisibleChange * 0.75, velocityThreshold = valueThreshold * 62.5.
 */
final class Spring {
    float value;
    float velocity;
    float target;
    double naturalFreq = Math.sqrt(1500.0);
    double dampingRatio = 1.0;
    float valueThreshold = 0.00075f;
    float velocityThreshold = 0.00075f * 62.5f;
    private long cachedDt = Long.MIN_VALUE;
    private double cachedW = Double.NaN, cachedZ = Double.NaN;
    private double decay, sin, cos, dampedW, gPlus, gMinus, decayPlus, decayMinus;
    // Integer-millisecond clocks commonly alternate between two frame intervals. Keep
    // their time-dependent coefficients without changing the solver's arithmetic.
    private long alternateDt = Long.MIN_VALUE;
    private double alternateA, alternateB, alternateC;
    private boolean alternateValid;

    static float responseToStiffness(float response) {
        // OplusPanelAnimationExImplKt.responseToStiffness / AnimatorUtils.responseToStiffness
        return (float) Math.pow(2 * Math.PI / (response == 0f ? 1.0 : response), 2.0);
    }

    Spring stiffness(double k) {
        naturalFreq = Math.sqrt(k);
        return this;
    }

    Spring response(float r) {
        naturalFreq = 2 * Math.PI / r;
        return this;
    }

    Spring damping(double ratio) {
        dampingRatio = ratio;
        return this;
    }

    Spring bounce(float b) {
        dampingRatio = 1.0 - b;
        return this;
    }

    Spring minimumVisibleChange(float change) {
        valueThreshold = Math.abs(change) * 0.75f;
        velocityThreshold = valueThreshold * 62.5f;
        return this;
    }

    void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    boolean isAtRest() {
        return Math.abs(velocity) < velocityThreshold && Math.abs(value - target) < valueThreshold;
    }

    /** Advances the spring by dtMs milliseconds. Returns true once it has settled on the target. */
    boolean step(long dtMs) {
        double t = dtMs / 1000.0;
        double w = naturalFreq;
        double z = dampingRatio;
        boolean alternate = false;
        if (cachedDt != dtMs || cachedW != w || cachedZ != z) {
            if (cachedW == w && cachedZ == z) {
                if (alternateValid && alternateDt == dtMs) {
                    alternate = true;
                } else {
                    alternateDt = cachedDt;
                    alternateA = z > 1 ? decayMinus : decay;
                    alternateB = z > 1 ? decayPlus : sin;
                    alternateC = cos;
                    alternateValid = true;
                }
            } else {
                // Both intervals belong to the same natural frequency and damping.
                alternateDt = Long.MIN_VALUE;
                alternateValid = false;
            }
            if (!alternate) {
                cachedDt = dtMs;
                cachedW = w;
                cachedZ = z;
                if (z > 1) {
                    double root = Math.sqrt(z * z - 1);
                    gPlus = -z * w + w * root;
                    gMinus = -z * w - w * root;
                    decayMinus = Math.exp(gMinus * t);
                    decayPlus = Math.exp(gPlus * t);
                } else if (z == 1) {
                    decay = Math.exp(-w * t);
                } else {
                    dampedW = w * Math.sqrt(1 - z * z);
                    decay = Math.exp(-z * w * t);
                    sin = Math.sin(dampedW * t);
                    cos = Math.cos(dampedW * t);
                }
            }
        }
        double x0 = value - target;
        double v0 = velocity;
        double x, v;
        if (z > 1) {
            double eMinus = alternate ? alternateA : decayMinus;
            double ePlus = alternate ? alternateB : decayPlus;
            double b = (gMinus * x0 - v0) / (gMinus - gPlus);
            double a = x0 - b;
            x = a * eMinus + b * ePlus;
            v = a * gMinus * eMinus + b * gPlus * ePlus;
        } else if (z == 1) {
            double a = x0;
            double b = v0 + w * x0;
            double e = alternate ? alternateA : decay;
            x = (a + b * t) * e;
            v = (a + b * t) * e * -w + b * e;
        } else {
            double wd = dampedW;
            double c = x0;
            double s = (z * w * x0 + v0) / wd;
            double e = alternate ? alternateA : decay;
            double sn = alternate ? alternateB : sin;
            double cs = alternate ? alternateC : cos;
            x = e * (c * cs + s * sn);
            v = x * -w * z + e * (-wd * c * sn + wd * s * cs);
        }
        value = (float) (x + target);
        velocity = (float) v;
        if (isAtRest()) {
            value = target;
            velocity = 0f;
            return true;
        }
        return false;
    }
}
