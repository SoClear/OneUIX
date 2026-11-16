package dev.colormotion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolves the panel's hot contracts once, instead of Xposed string/argument lookup per frame. */
final class PanelFields {
    private static final Object[] NO_ARGS = new Object[0];
    private final Field bar, fraction, expandedHeight, initialOffset, closed, split, side, velocity;
    private final Method tracking, maxDistance, setHeight;
    private final Object[] heightArg = new Object[1];

    PanelFields(Class<?> panel) throws ReflectiveOperationException {
        bar = panel.getField("mBarState");
        fraction = panel.getField("mExpandedFraction");
        Field nativeHeight;
        try { nativeHeight = panel.getField("mExpandedHeight"); }
        catch (NoSuchFieldException absent) { nativeHeight = null; }
        if (nativeHeight != null && nativeHeight.getType() != float.class) nativeHeight = null;
        expandedHeight = nativeHeight;
        if (expandedHeight != null) expandedHeight.setAccessible(true);
        initialOffset = panel.getField("mInitialOffsetOnTouch");
        closed = panel.getField("mPanelClosedOnDown");
        split = panel.getField("mPanelSplitHelper");
        side = split.getType().getField("currentState");
        velocity = panel.getField("mVelocityTracker");
        tracking = panel.getMethod("isTracking");
        maxDistance = panel.getMethod("getMaxPanelTransitionDistance");
        setHeight = panel.getMethod("setExpandedHeightInternal", float.class);
        for (Field f : new Field[] {bar, fraction, initialOffset, closed, split, side, velocity}) f.setAccessible(true);
        for (Method m : new Method[] {tracking, maxDistance, setHeight}) m.setAccessible(true);
    }

    int barState(Object panel) {
        try { return bar.getInt(panel); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    float fraction(Object panel) {
        try { return fraction.getFloat(panel); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    float initialOffset(Object panel) {
        try { return initialOffset.getFloat(panel); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    /** Native height from the same consumer that writes mExpandedFraction; no geometry query. */
    float expandedHeight(Object panel) {
        if (expandedHeight == null) return Float.NaN;
        try { return expandedHeight.getFloat(panel); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    boolean closedOnDown(Object panel) {
        try { return closed.getBoolean(panel); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    int side(Object panel) {
        try {
            Object helper = split.get(panel);
            return helper != null ? side.getInt(helper) : 1;
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    Object velocityTracker(Object panel) {
        try { return velocity.get(panel); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    boolean tracking(Object panel) {
        try { return (Boolean) tracking.invoke(panel, NO_ARGS); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    float maxDistance(Object panel) {
        try { return ((Number) maxDistance.invoke(panel, NO_ARGS)).floatValue(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    void setHeight(Object panel, float value) {
        heightArg[0] = value;
        try { setHeight.invoke(panel, heightArg); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
