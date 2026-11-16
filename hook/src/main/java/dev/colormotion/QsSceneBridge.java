package dev.colormotion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.util.Property;
import android.view.View;

import de.robv.android.xposed.XposedHelpers;

/** Keeps the native QS content composed at its final geometry while our layers own its reveal. */
final class QsSceneBridge {
    private static final Float CONTENT_HIDDEN = Float.valueOf(0f), CONTENT_SHOWN = Float.valueOf(1f);
    private static final Object[] NO_ARGS = new Object[0];
    private final Field composeEnabled;
    private final Field[] excludedTransitions;
    private final Field openPosition, userChanged, adapter, expansion;
    private final Field[] animators;
    private final boolean separateButtons;
    private final Method setPosition, getValue, updateState;
    private final Field lastPosition;
    private final Object[] positionArg = new Object[1];
    private final Object[] flowArgs = new Object[2];
    private final DirectBinding direct;
    private final Object[] plannedAnimators;
    private final DirectPlan[] plans;
    private final RejectedShape[] rejectedShapes;

    QsSceneBridge(ClassLoader cl) throws ReflectiveOperationException {
        Class<?> split = XposedHelpers.findClass("com.android.systemui.qs.animator.PanelSplitOpenAnimator", cl);
        Class<?> flags = XposedHelpers.findClass("com.android.systemui.qs.animator.QsAnimatorState", cl);
        composeEnabled = XposedHelpers.findClass("com.android.systemui.QpRune", cl).getField("QUICK_PANEL_COMPOSE");
        String[] exclusions = {"isSliding", "isDetailShowing", "isDetailOpening", "isDetailClosing",
                "isDetailPopupShowing", "isDetailPopupClosing", "isCustomizerShowing"};
        java.util.ArrayList<Field> present = new java.util.ArrayList<>();
        for (String name : exclusions) {
            try { present.add(flags.getField(name)); }
            catch (NoSuchFieldException missing) {
                // Popup transitions were removed in One UI 9. Other transition flags remain
                // required; an unknown ABI must roll back the complete hook installation.
                if (!name.startsWith("isDetailPopup")) throw missing;
            }
        }
        excludedTransitions = present.toArray(new Field[0]);
        openPosition = split.getField("openPosition");
        userChanged = split.getField("mUserChanged");
        adapter = split.getField("composeAdapter");
        expansion = adapter.getType().getField("_expansionFraction");
        java.util.ArrayList<Field> nativeAnimators = new java.util.ArrayList<>();
        for (String name : new String[] {"headerAnimator", "blurViewAnimator", "clockDateAnimator"})
            nativeAnimators.add(split.getField(name));
        // Both APKs declare buttonAnimator, but only the newer Compose branch drives it.
        // The removed popup flags distinguish these two audited native scene contracts.
        separateButtons = present.size() == 5;
        if (separateButtons) nativeAnimators.add(split.getField("buttonAnimator"));
        animators = nativeAnimators.toArray(new Field[0]);
        setPosition = animators[0].getType().getMethod("setPosition", float.class);
        lastPosition = animators[0].getType().getField("mLastT");
        lastPosition.setAccessible(true);
        DirectBinding candidate;
        try { candidate = DirectBinding.bind(animators[0].getType(), cl, lastPosition); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { candidate = null; }
        direct = candidate;
        plannedAnimators = new Object[animators.length];
        plans = new DirectPlan[animators.length];
        rejectedShapes = new RejectedShape[animators.length];
        Class<?> flow = expansion.getType();
        getValue = flow.getMethod("getValue");
        updateState = flow.getDeclaredMethod("updateState", Object.class, Object.class);
        for (Field f : new Field[] {composeEnabled, openPosition,
                userChanged, adapter, expansion}) f.setAccessible(true);
        for (Field f : excludedTransitions) f.setAccessible(true);
        for (Field f : animators) f.setAccessible(true);
        setPosition.setAccessible(true);
        getValue.setAccessible(true);
        updateState.setAccessible(true);
    }

    boolean apply(Object animator, float fraction) throws ReflectiveOperationException {
        if (!composeEnabled.getBoolean(null)) return false;
        for (Field f : excludedTransitions) if (f.getBoolean(null)) return false;
        // Preserve Samsung's bookkeeping and native header/blur alpha timing. Only its Compose
        // visual expansion is replaced: emitting every intermediate fraction otherwise rebuilds
        // the QS scene and header underneath the independent ColorOS row transforms.
        openPosition.setFloat(animator, fraction);
        userChanged.setBoolean(animator, false);
        for (int i = 0; i < animators.length; i++) {
            Object touchAnimator = animators[i].get(animator);
            position(touchAnimator, fraction, i);
        }
        Object flow = expansion.get(adapter.get(animator));
        float content = contentFraction(fraction);
        if (((Number) getValue.invoke(flow, NO_ARGS)).floatValue() != content) {
            flowArgs[1] = content == 0f ? CONTENT_HIDDEN : CONTENT_SHOWN;
            updateState.invoke(flow, flowArgs);
        }
        return true;
    }

    boolean hasSeparateButtons() { return separateButtons; }


    static float contentFraction(float fraction) {
        return fraction > 0f ? 1f : 0f;
    }

    /** Restore Samsung's actual pose, including alpha=0 on collapse, rather than flashing identity. */
    void restoreHeaderPose(Object animator, float fraction) throws ReflectiveOperationException {
        for (int i = 0; i < animators.length; i++) {
            if (i == 1) continue; // Blur views have their own native ownership.
            Object target = animators[i].get(animator);
            if (target == null) { position(null, fraction, i); continue; }
            // The native animator can already have cached this position before our overrides.
            lastPosition.setFloat(target, Float.NaN);
            position(target, fraction, i);
        }
    }

    private void position(Object animator, float fraction, int slot) throws ReflectiveOperationException {
        if (animator == null) {
            plannedAnimators[slot] = null;
            plans[slot] = null;
            rejectedShapes[slot] = null;
            return;
        }
        if (direct != null) {
            if (plannedAnimators[slot] != animator || (plans[slot] != null && !plans[slot].matches())
                    || (rejectedShapes[slot] != null && !rejectedShapes[slot].matches())) {
                plannedAnimators[slot] = animator;
                plans[slot] = null;
                rejectedShapes[slot] = null;
                try {
                    plans[slot] = direct.plan(animator);
                    if (plans[slot] == null) rejectedShapes[slot] = direct.rejectedShape(animator);
                }
                catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { /* native fallback */ }
            }
            DirectPlan plan = plans[slot];
            if (plan != null) {
                plan.apply(fraction);
                return;
            }
        }
        if (positionArg[0] == null || ((Float) positionArg[0]).floatValue() != fraction) positionArg[0] = fraction;
        setPosition.invoke(animator, positionArg);
    }

    /**
     * The audited listener-free split animators only forward float properties to Views.
     * QSAnimView's alpha wrapper additionally captures caller stacks/timestamps for dump output
     * (on every call in 8.5). Replay that exact keyframe math on the wrapped View, retaining
     * native execution for unknown shapes, interpolators, listeners and wrapper subclasses.
     */
    private static final class DirectBinding {
        private final Class<?> animatorType, keyframeType, wrapperType;
        private final Field targets, keyframes, listener, interpolator, start, span, last;
        private final Field property, values, size, width, view;

        static DirectBinding bind(Class<?> type, ClassLoader cl, Field last)
                throws ReflectiveOperationException {
            if (!type.getName().equals("com.android.systemui.qs.TouchAnimator")) return null;
            return new DirectBinding(type, cl, last);
        }

        private DirectBinding(Class<?> type, ClassLoader cl, Field last) throws ReflectiveOperationException {
            animatorType = type;
            keyframeType = Class.forName(type.getName() + "$FloatKeyframeSet", false, cl);
            wrapperType = Class.forName("com.android.systemui.qs.animator.QSAnimView", false, cl);
            targets = accessible(type.getField("mTargets"));
            keyframes = accessible(type.getField("mKeyframeSets"));
            listener = accessible(type.getField("mListener"));
            interpolator = accessible(type.getField("mInterpolator"));
            start = accessible(type.getField("mStartDelay"));
            span = accessible(type.getField("mSpan"));
            this.last = last;
            property = accessible(keyframeType.getField("mProperty"));
            values = accessible(keyframeType.getField("mValues"));
            size = accessible(keyframeType.getField("mSize"));
            width = accessible(keyframeType.getField("mFrameWidth"));
            view = accessible(wrapperType.getDeclaredField("view"));
        }

        private static Field accessible(Field field) { field.setAccessible(true); return field; }

        RejectedShape rejectedShape(Object animator) throws IllegalAccessException {
            if (animator.getClass() != animatorType) return null;
            return new RejectedShape((Object[]) targets.get(animator), (Object[]) keyframes.get(animator));
        }

        DirectPlan plan(Object animator) throws ReflectiveOperationException {
            if (animator.getClass() != animatorType || listener.get(animator) != null
                    || interpolator.get(animator) != null) return null;
            Object[] target = (Object[]) targets.get(animator), frames = (Object[]) keyframes.get(animator);
            if (target.length != frames.length) return null;
            View[] views = new View[target.length];
            int[] properties = new int[target.length];
            float[][] samples = new float[target.length][];
            float[] widths = new float[target.length];
            for (int i = 0; i < target.length; i++) {
                if (target[i] == null || target[i].getClass() != wrapperType
                        || frames[i] == null || frames[i].getClass() != keyframeType) return null;
                Property<?, ?> p = (Property<?, ?>) property.get(frames[i]);
                // Property.of(QSAnimView.class, float.class, name) in both native Builders.
                if (!p.getClass().getName().equals("android.util.ReflectiveProperty")
                        || p.getType() != float.class) return null;
                int code;
                switch (p.getName()) {
                    case "alpha": code = 0; break;
                    case "translationX": code = 1; break;
                    case "translationY": code = 2; break;
                    case "scaleX": code = 3; break;
                    case "scaleY": code = 4; break;
                    default: return null;
                }
                views[i] = (View) view.get(target[i]);
                properties[i] = code;
                samples[i] = (float[]) values.get(frames[i]);
                if (samples[i].length < 2 || size.getInt(frames[i]) != samples[i].length) return null;
                widths[i] = width.getFloat(frames[i]);
                if (!(widths[i] > 0f) || !Float.isFinite(widths[i])) return null;
            }
            return new DirectPlan(animator, last, start.getFloat(animator), span.getFloat(animator),
                    target, frames, views, properties, samples, widths);
        }
    }

    /** Retain only a rejected shape, so element replacement can regain the guarded path. */
    private static final class RejectedShape {
        private final Object[] targets, keyframes, originalTargets, originalKeyframes;
        RejectedShape(Object[] targets, Object[] keyframes) {
            this.targets = targets; this.keyframes = keyframes;
            originalTargets = targets.clone(); originalKeyframes = keyframes.clone();
        }
        boolean matches() {
            for (int i = 0; i < targets.length; i++)
                if (targets[i] != originalTargets[i]) return false;
            for (int i = 0; i < keyframes.length; i++)
                if (keyframes[i] != originalKeyframes[i]) return false;
            return true;
        }
    }

    private static final class DirectPlan {
        private final Object animator;
        private final Field last;
        private final float start, span;
        private final View[] views;
        private final int[] properties;
        private final float[][] samples;
        private final float[] widths;
        private final boolean sharedGeometry;
        private final Object[] targets, keyframes, originalTargets, originalKeyframes;

        DirectPlan(Object animator, Field last, float start, float span, Object[] targets, Object[] keyframes, View[] views,
                int[] properties, float[][] samples, float[] widths) {
            this.animator = animator; this.last = last; this.start = start; this.span = span;
            this.views = views; this.properties = properties; this.samples = samples; this.widths = widths;
            this.targets = targets; this.keyframes = keyframes;
            originalTargets = targets.clone(); originalKeyframes = keyframes.clone();
            boolean shared = samples.length > 0;
            for (int i = 1; i < samples.length; i++) {
                if (samples[i].length != samples[0].length || widths[i] != widths[0]) shared = false;
            }
            sharedGeometry = shared;
        }

        boolean matches() {
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] != originalTargets[i] || keyframes[i] != originalKeyframes[i]) return false;
            }
            return true;
        }

        void apply(float fraction) throws IllegalAccessException {
            if (Float.isNaN(fraction)) return;
            float t = (fraction - start) / span;
            if (t < 0f) t = 0f;
            else if (t > 1f) t = 1f;
            if (t == last.getFloat(animator)) return;
            // Native TouchAnimator only writes mLastT inside its listener branch. These
            // listener-free plans intentionally keep that behavior, including repeat writes
            // that can repair a pose changed by another native/module consumer.
            int sharedIndex = 0;
            float sharedFraction = 0f;
            if (sharedGeometry) {
                float width = widths[0];
                sharedIndex = Math.max(1, Math.min((int) Math.ceil(t / width), samples[0].length - 1));
                sharedFraction = (t - ((sharedIndex - 1) * width)) / width;
            }
            for (int k = 0; k < views.length; k++) {
                View v = views[k];
                if (v == null) continue;
                float[] values = samples[k];
                float width = widths[k];
                // Native split animators give every property the same keyframe geometry.
                // Share only its index/fraction math; retain every property write and sample.
                int i = sharedGeometry ? sharedIndex
                        : Math.max(1, Math.min((int) Math.ceil(t / width), values.length - 1));
                float f = sharedGeometry ? sharedFraction : (t - ((i - 1) * width)) / width;
                float previous = values[i - 1];
                float value = ((values[i] - previous) * f) + previous;
                switch (properties[k]) {
                    case 0: v.setAlpha(value); break;
                    case 1: v.setTranslationX(value); break;
                    case 2: v.setTranslationY(value); break;
                    case 3: v.setScaleX(value); break;
                    case 4: v.setScaleY(value); break;
                }
            }
        }
    }
}
