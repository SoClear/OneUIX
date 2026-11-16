package dev.colormotion;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XposedHelpers;

/** Adds reversible depth/crossfade to Samsung's native horizontal panel-switch gesture. */
final class PanelSlideMotion {
    private static final Object[] NO_ARGS = new Object[0];
    final Method slide;
    private final Field state, qsRoot, shadeRoot, displayMode;
    private final Method getView, popOver;
    private final Field[] exclusions;
    private View lastQs, lastShade;
    private final float[] qsNative = new float[4], shadeNative = new float[4];
    private final float[] qsOwned = new float[4], shadeOwned = new float[4];
    private boolean hasPose;

    PanelSlideMotion(ClassLoader cl) throws ReflectiveOperationException {
        Class<?> owner = XposedHelpers.findClass("com.android.systemui.qs.animator.PanelTransitionAnimator", cl);
        Class<?> direction = XposedHelpers.findClass("com.android.systemui.shade.PanelSlideEventHandler$Direction", cl);
        slide = owner.getMethod("slide", float.class, float.class, direction, int.class);
        state = owner.getField("panelTransitionState");
        qsRoot = owner.getField("qsRootView"); shadeRoot = owner.getField("shadeRootView");
        getView = qsRoot.getType().getMethod("getView");
        displayMode = owner.getField("secQsUiDisplayModeInteractor");
        popOver = displayMode.getType().getMethod("isPopOverEnabled");
        Class<?> flags = XposedHelpers.findClass("com.android.systemui.qs.animator.QsAnimatorState", cl);
        String[] names = {"isDetailShowing", "isDetailOpening", "isDetailClosing", "isCustomizerShowing"};
        java.util.ArrayList<Field> excluded = new java.util.ArrayList<>();
        for (String name : names) excluded.add(flags.getField(name));
        for (String name : new String[] {"isDetailPopupShowing", "isDetailPopupClosing"}) {
            try { excluded.add(flags.getField(name)); }
            catch (NoSuchFieldException removedInOneUi9) { /* audited optional contracts */ }
        }
        exclusions = excluded.toArray(new Field[0]);
    }

    int state(Object animator) throws IllegalAccessException { return state.getInt(animator); }

    boolean isHorizontal(Object[] args) {
        if (args[2] == null) return false;
        String name = ((Enum<?>) args[2]).name();
        return name.equals("LEFT") || name.equals("RIGHT");
    }

    boolean isPanelTransition(Object[] args) {
        return isHorizontal(args) || (args[2] instanceof Enum<?> && ((Enum<?>) args[2]).name().equals("DOWN"));
    }

    boolean allowed(Object animator) throws ReflectiveOperationException {
        for (Field f : exclusions) if (f.getBoolean(null)) return false;
        return !(Boolean) popOver.invoke(displayMode.get(animator), NO_ARGS);
    }

    void apply(Object animator, Object[] args) throws ReflectiveOperationException {
        Object q = qsRoot.get(animator), n = shadeRoot.get(animator);
        View qs = q == null ? null : (View) getView.invoke(q, NO_ARGS);
        View shade = n == null ? null : (View) getView.invoke(n, NO_ARGS);
        if (lastQs != qs || lastShade != shade) abort();
        lastQs = qs; lastShade = shade;
        // TouchAnimator may skip an identical fraction. Capture only native changes,
        // retaining its earlier pose if this consumer still contains our last override.
        capture(qs, qsNative, qsOwned);
        capture(shade, shadeNative, shadeOwned);
        float f = QsModel.clamp((Float) args[0], 0f, 1f);
        int target = (Integer) args[3];
        float qsVisible = target == 0 ? f : 1f - f;
        float shadeVisible = 1f - qsVisible;
        // Native Compose already supplies QS's 300px horizontal travel, including reversed
        // panels and RTL. Do not apply that displacement a second time to its Android host.
        pose(qs, qsVisible, 0f);
        read(qs, qsOwned);
        if (shade != null) {
            float sign = ((Enum<?>) args[2]).name().equals("LEFT") ? -1f : 1f;
            float travel = shade.getWidth() * 0.28f;
            float x = target == 1 ? -sign * travel * (1f - f) : sign * travel * f;
            pose(shade, shadeVisible, x);
        }
        read(shade, shadeOwned);
        hasPose = true;
    }

    private static void pose(View v, float visibility, float x) {
        if (v == null) return;
        float alpha = visibility * visibility * (3f - 2f * visibility);
        float scale = 0.96f + 0.04f * visibility;
        if (v.getAlpha() != alpha) v.setAlpha(alpha);
        if (v.getTranslationX() != x) v.setTranslationX(x);
        if (v.getScaleX() != scale) v.setScaleX(scale);
        if (v.getScaleY() != scale) v.setScaleY(scale);
    }

    void reset() {
        for (View v : new View[] {lastQs, lastShade}) if (v != null) {
            if (v.getTranslationX() != 0f) v.setTranslationX(0f);
            if (v.getScaleX() != 1f) v.setScaleX(1f);
            if (v.getScaleY() != 1f) v.setScaleY(1f);
        }
        lastQs = lastShade = null;
        hasPose = false;
    }

    private void capture(View v, float[] nativePose, float[] ownedPose) {
        if (v == null) return;
        float a = v.getAlpha(), x = v.getTranslationX(), sx = v.getScaleX(), sy = v.getScaleY();
        if (!hasPose || a != ownedPose[0]) nativePose[0] = a;
        if (!hasPose || x != ownedPose[1]) nativePose[1] = x;
        if (!hasPose || sx != ownedPose[2]) nativePose[2] = sx;
        if (!hasPose || sy != ownedPose[3]) nativePose[3] = sy;
    }

    private static void read(View v, float[] pose) {
        if (v == null) return;
        pose[0] = v.getAlpha(); pose[1] = v.getTranslationX();
        pose[2] = v.getScaleX(); pose[3] = v.getScaleY();
    }

    private static void restore(View v, float[] nativePose, float[] ownedPose) {
        if (v == null) return;
        if (v.getAlpha() == ownedPose[0] && v.getAlpha() != nativePose[0]) v.setAlpha(nativePose[0]);
        if (v.getTranslationX() == ownedPose[1] && v.getTranslationX() != nativePose[1]) v.setTranslationX(nativePose[1]);
        if (v.getScaleX() == ownedPose[2] && v.getScaleX() != nativePose[2]) v.setScaleX(nativePose[2]);
        if (v.getScaleY() == ownedPose[3] && v.getScaleY() != nativePose[3]) v.setScaleY(nativePose[3]);
    }

    /** Restore before the next native consumer, and on mode changes with no native endpoint. */
    void abort() {
        if (hasPose) {
            restore(lastQs, qsNative, qsOwned);
            restore(lastShade, shadeNative, shadeOwned);
        }
        lastQs = lastShade = null;
        hasPose = false;
    }
}
