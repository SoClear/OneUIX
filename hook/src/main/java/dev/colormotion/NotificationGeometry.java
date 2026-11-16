package dev.colormotion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The notification cascade reveals a laid-out list, as the QS layers do. Keep Samsung's
 * stack at its expanded geometry inside its layout consumers, without changing the real
 * panel fraction used by input, window visibility, scrims or the rest of SystemUI.
 */
final class NotificationGeometry {
    private final Field ambient, fraction, updater, trackedHeadsUp;
    private final Field pinned, headsUpAway, launching;
    private final Field expandedHeight;
    private final Field scrollY, beingDragged;
    private final Field[] eventWork;
    private final Method setHeight;
    private final Object[] heightArg = new Object[1];
    private final Object[] owners = new Object[16];
    private int depth;
    private Object currentStack, currentAmbient;
    private float savedFraction;

    NotificationGeometry(Class<?> stack) throws ReflectiveOperationException {
        ambient = field(stack, "mAmbientState");
        fraction = field(ambient.getType(), "mExpansionFraction");
        trackedHeadsUp = field(ambient.getType(), "mTrackedHeadsUpRow");
        updater = field(stack, "mChildrenUpdater");
        pinned = field(stack, "mInHeadsUpPinnedMode");
        headsUpAway = field(stack, "mHeadsUpAnimatingAway");
        launching = field(stack, "mLaunchingNotification");
        expandedHeight = field(stack, "mExpandedHeight");
        scrollY = field(stack, "mOwnScrollY");
        beingDragged = field(stack, "mIsBeingDragged");
        String[] events = {"mNeedsAnimation", "mAnimationRunning", "mClearAllInProgress", "mIsBeingDragged"};
        eventWork = new Field[events.length];
        for (int i = 0; i < events.length; i++) eventWork[i] = field(stack, events[i]);
        setHeight = stack.getMethod("setExpandedHeight", float.class);
        setHeight.setAccessible(true);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    Object childrenUpdater(Object stack) throws IllegalAccessException { return updater.get(stack); }

    boolean scrolling(Object stack) throws IllegalAccessException {
        return beingDragged.getBoolean(stack) || scrollY.getInt(stack) != 0;
    }

    boolean fingerScrolling(Object stack) throws IllegalAccessException { return beingDragged.getBoolean(stack); }
    int scrollPosition(Object stack) throws IllegalAccessException { return scrollY.getInt(stack); }

    boolean canDeferHiddenUpdate(Object stack) throws IllegalAccessException {
        if (pinned.getBoolean(stack) || headsUpAway.getBoolean(stack) || launching.getBoolean(stack)
                || trackedHeadsUp.get(ambient.get(stack)) != null) return false;
        for (Field f : eventWork) if (f.getBoolean(stack)) return false;
        return true;
    }

    void restoreHeight(Object stack, float height) throws ReflectiveOperationException {
        if (expandedHeight.getFloat(stack) == height) return;
        heightArg[0] = height;
        try { setHeight.invoke(stack, heightArg); }
        finally { heightArg[0] = null; }
    }

    boolean enter(Object owner, Object stack) throws IllegalAccessException {
        if (depth == owners.length || (depth != 0 && currentStack != stack)) return false;
        Object state = ambient.get(stack);
        // Heads-up drags and notification launches retain their native geometry/clip behavior.
        if (pinned.getBoolean(stack) || headsUpAway.getBoolean(stack)
                || launching.getBoolean(stack) || trackedHeadsUp.get(state) != null) return false;
        if (depth == 0) {
            savedFraction = fraction.getFloat(state);
            if (!(savedFraction > 0f)) return false;
            currentStack = stack;
            currentAmbient = state;
            fraction.setFloat(state, 1f);
        }
        owners[depth++] = owner;
        return true;
    }

    /** Also called on native exceptions; nested consumers cannot restore an outer scope early. */
    void exit(Object owner) throws IllegalAccessException {
        if (depth == 0 || owners[depth - 1] != owner) return;
        owners[--depth] = null;
        if (depth == 0) {
            try { fraction.setFloat(currentAmbient, savedFraction); }
            finally { currentAmbient = null; currentStack = null; }
        }
    }
}
