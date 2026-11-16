package dev.colormotion;

/** Last pose published to a rendering layer. Invisible changes can wait until the reveal. */
final class MotionPose {
    private float alpha = Float.NaN, scale, y;

    boolean update(float nextAlpha, float nextScale, float nextY) {
        if (alpha == nextAlpha && (nextAlpha == 0f || (scale == nextScale && y == nextY))) return false;
        alpha = nextAlpha;
        scale = nextScale;
        y = nextY;
        return true;
    }
}
