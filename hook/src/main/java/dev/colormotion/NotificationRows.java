package dev.colormotion;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Applies the reference-fitted QS flow to notification cards, one card per motion node. */
final class NotificationRows {
    private static final Object[] NO_ARGS = new Object[0];
    final QsModel model = new QsModel();
    private final ArrayList<State> pool = new ArrayList<>();
    private final ArrayList<State> applied = new ArrayList<>();
    private Field stackAlgorithm, algorithmState, visibleChildren, stackAmbient, stackY, childState, layoutPending;
    private Field stateY, stateScaleX, stateScaleY, stateAlpha;
    private Method topPadding, mustStay, isAnimating, setFaded;
    private Field stateGone, stateHidden, willBeGone, faded;
    private Class<?> notificationRow;
    private Field actualHeight, topFadeEnd, bottomFadeStart;
    private Field scrollY;
    private int firstRow;
    private Method updateGradient, attachedChildren;
    private float lastFadeTop=Float.NaN, lastFadeBottom=Float.NaN;
    private final Object[] childArg = new Object[1];
    private final Object[] fadeArg = new Object[1];
    private NativeNotifications nativeRows;

    void installNative(NativeLayers bindings) {
        if (bindings == null) return;
        try {
            nativeRows = new NativeNotifications(bindings, model);
            de.robv.android.xposed.XposedBridge.log(Entry.TAG + ": typed notification bridge ready");
        } catch (Throwable failure) { disableNative(failure); }
    }

    private void disableNative(Throwable failure) {
        nativeRows = null;
        de.robv.android.xposed.XposedBridge.log(Entry.TAG + ": notification bridge unavailable, using reflective binding: " + failure);
    }

    NotificationRows() {
        model.rowCount = 1;
        model.reset();
    }

    private List<?> children(Object stack) throws IllegalAccessException {
        return (List<?>) visibleChildren.get(algorithmState.get(stackAlgorithm.get(stack)));
    }

    boolean isBound() { return layoutPending != null; }

    boolean layoutPending(Object stack) throws IllegalAccessException {
        return layoutPending.getBoolean(stack);
    }

    void prepare(Object stack) throws IllegalAccessException {
        if (nativeRows != null) {
            try { nativeRows.prepare(stack); return; }
            catch (LinkageError | ClassCastException failure) { disableNative(failure); }
        }
        List<?> children = children(stack);
        firstRow = firstVisibleRow(stack, children);
        model.setRowCount(children.size() - firstRow);
    }

    /** Resolve metadata once; no string-based field lookup or snapshot allocation per frame. */
    void bind(Class<?> stack, Class<?> expandableView) throws ReflectiveOperationException {
        stackAlgorithm = stack.getField("mStackScrollAlgorithm");
        algorithmState = stackAlgorithm.getType().getField("mTempAlgorithmState");
        visibleChildren = algorithmState.getType().getField("visibleChildren");
        stackAmbient = stack.getField("mAmbientState");
        layoutPending = stack.getField("mChildrenUpdateRequested");
        scrollY = stack.getField("mOwnScrollY");
        stackY = stackAmbient.getType().getField("mStackY");
        childState = expandableView.getField("mViewState");
        Class<?> state = childState.getType();
        stateY = state.getField("mYTranslation");
        stateScaleX = state.getField("mScaleX");
        stateScaleY = state.getField("mScaleY");
        stateAlpha = state.getField("mAlpha");
        topPadding = stack.getMethod("getTopPadding");
        mustStay = expandableView.getMethod("mustStayOnScreen");
        for (Field field : new Field[] {stackAlgorithm, algorithmState, visibleChildren, stackAmbient,
                stackY, childState, stateY, stateScaleX, stateScaleY, stateAlpha, layoutPending, scrollY}) field.setAccessible(true);
        topPadding.setAccessible(true);
        mustStay.setAccessible(true);
    }

    /** Extra native contracts used by render-only frames; native layout/event animations still run. */
    void bindRendering(Class<?> row) throws ReflectiveOperationException {
        notificationRow = row;
        actualHeight = childState.getDeclaringClass().getField("mActualHeight");
        topFadeEnd = stackAmbient.getType().getField("mBlurTopFadeoutEnd");
        bottomFadeStart = stackAmbient.getType().getField("mBlurBottomFadeoutStart");
        updateGradient = row.getMethod("updateBlurBackgroundGradient");
        attachedChildren = row.getMethod("getAttachedChildren");
        for (Field f : new Field[] {actualHeight, topFadeEnd, bottomFadeStart}) f.setAccessible(true);
        updateGradient.setAccessible(true);
        attachedChildren.setAccessible(true);
        Class<?> state = childState.getType();
        stateGone = state.getField("gone");
        stateHidden = state.getField("hidden");
        willBeGone = childState.getDeclaringClass().getField("mWillBeGone");
        faded = row.getField("mIsFaded");
        setFaded = row.getMethod("setNotificationFaded", boolean.class);
        isAnimating = state.getMethod("isAnimating", View.class);
        for (Field f : new Field[] {stateGone, stateHidden, willBeGone, faded}) f.setAccessible(true);
        setFaded.setAccessible(true);
        isAnimating.setAccessible(true);
    }

    /**
     * Apply only the four animated properties from the latest native layout. In particular this
     * does not schedule NSSL's complete stack algorithm, shelf pass or location callbacks.
     * Native layout consumers above still receive the same temporary state overrides when
     * expansion, scrolling, notification events or geometry actually require a layout pass.
     */
    void render(Object stack) throws ReflectiveOperationException {
        render(stack, false);
    }

    /**
     * Frame-clock fast path: call prepare(stack), step the model, then this method in the same
     * synchronous callback. No native layout/scroll/list mutation may occur between those calls.
     * Unlike a cross-frame cache, this only reuses the first-row scan already done for this step.
     */
    void renderPrepared(Object stack) throws ReflectiveOperationException {
        render(stack, true);
    }

    private void render(Object stack, boolean prepared) throws ReflectiveOperationException {
        if (nativeRows != null) {
            try {
                if (prepared) nativeRows.renderPrepared(stack);
                else nativeRows.render(stack);
                return;
            }
            catch (LinkageError | ClassCastException failure) { disableNative(failure); }
            // The typed adapter owned prepare's first-row result. Recompute on fallback.
            prepared = false;
        }
        // The pending native pre-draw will consume our latest pose through apply()/restore().
        // Avoid writing the same properties twice before that traversal.
        if (layoutPending.getBoolean(stack)) return;
        List<?> children = children(stack);
        if (!prepared) {
            firstRow = firstVisibleRow(stack, children);
            model.setRowCount(children.size() - firstRow);
        }
        Object ambient = stackAmbient.get(stack);
        float fadeTop = topFadeEnd.getFloat(ambient), fadeBottom = bottomFadeStart.getFloat(ambient);
        boolean fadeChanged = fadeTop != lastFadeTop || fadeBottom != lastFadeBottom;
        float stackOffset = ((Number) topPadding.invoke(stack, NO_ARGS)).floatValue()
                - stackY.getFloat(ambient) + model.translation.value;
        int viewport = stack instanceof View ? ((View) stack).getHeight() : 0;
        try {
            for (int i = firstRow; i < children.size(); i++) {
                View child = (View) children.get(i);
                if (child.isPressed() || (Boolean) mustStay.invoke(child, NO_ARGS)
                        || willBeGone.getBoolean(child)) continue;
                Object state = childState.get(child);
                if (state == null || stateGone.getBoolean(state)) continue;
                childArg[0] = child;
                if ((Boolean) isAnimating.invoke(null, childArg)) continue;
                int row = Math.min(i - firstRow, QsModel.MAX_ROWS - 1);
                float alpha = model.rowAlpha(row);
                float scale = model.scale[row].value;
                float y = stateY.getFloat(state) + stackOffset + model.rowTranslation(row);
                float previousY = child.getTranslationY();
                int cardHeight = actualHeight.getInt(child);
                if (!onStack(y, cardHeight, viewport) && !onStack(previousY, cardHeight, viewport)) {
                    // Same off-stack skip as the typed bridge.
                    if (notificationRow.isInstance(child) && faded.getBoolean(child)) {
                        fadeArg[0] = Boolean.FALSE;
                        setFaded.invoke(child, fadeArg);
                    }
                    continue;
                }
                if (previousY != y) child.setTranslationY(y);
                if (child.getScaleX() != scale) child.setScaleX(scale);
                if (child.getScaleY() != scale) child.setScaleY(scale);
                boolean hidden = stateHidden.getBoolean(state) || alpha == 0f;
                // Off-stack cards draw nothing; do not build their fade hardware layers.
                boolean nextFaded = !hidden && alpha != 1f && onStack(y, actualHeight.getInt(child), viewport);
                if (notificationRow.isInstance(child)) {
                    if (faded.getBoolean(child) != nextFaded) {
                        fadeArg[0] = nextFaded ? Boolean.TRUE : Boolean.FALSE;
                        setFaded.invoke(child, fadeArg);
                    }
                } else {
                    int layer = nextFaded && child.hasOverlappingRendering() ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
                    if (child.getLayerType() != layer) child.setLayerType(layer, null);
                }
                if (child.getAlpha() != alpha) child.setAlpha(alpha);
                int visibility = hidden ? View.INVISIBLE : View.VISIBLE;
                int previousVisibility = child.getVisibility();
                boolean becameVisible = visibility == View.VISIBLE && previousVisibility != View.VISIBLE;
                if (previousVisibility != visibility) child.setVisibility(visibility);
                // A gated/delayed INVISIBLE card cannot contribute pixels. Native gradients
                // allocate blur descriptors; defer that work until the card is visible again.
                // Refresh even at an unchanged central Y on reentry to clear its old edge mask.
                if (!hidden && (previousY != y || fadeChanged || becameVisible) && notificationRow.isInstance(child))
                    refreshGradient(child, previousY, y, fadeTop, fadeBottom, fadeChanged, becameVisible, viewport);
            }
            lastFadeTop = fadeTop;
            lastFadeBottom = fadeBottom;
        } finally {
            childArg[0] = null;
            fadeArg[0] = null;
        }
    }

    private void refreshGradient(View child, float oldY, float y, float top, float bottom,
            boolean changed, boolean force, int viewport)
            throws ReflectiveOperationException {
        int height = actualHeight.getInt(child);
        // Same off-stack cull as the typed bridge; native layout passes refresh all cards.
        if (!onStack(y, height, viewport)) return;
        if (height <= 0 || !(force || crossesFade(oldY, y, height, top, bottom)
                || (changed && (Float.isNaN(lastFadeTop)
                || crossesFade(oldY, y, height, lastFadeTop, lastFadeBottom))))) return;
        // The native shelf normally does this. Render-only motion bypasses that shelf pass,
        // so an old background gradient can otherwise remain after its card leaves the band.
        updateGradient.invoke(child, NO_ARGS);
        List<?> attached = (List<?>) attachedChildren.invoke(child, NO_ARGS);
        if (attached == null) return;
        for (int i = 0; i < attached.size(); i++) {
            View nested = (View) attached.get(i);
            if (nested.getVisibility() == View.VISIBLE && actualHeight.getInt(nested) > 0)
                updateGradient.invoke(nested, NO_ARGS);
        }
    }

    static boolean onStack(float y, int height, int viewport) {
        return viewport <= 0 || (y < viewport + height && y + 2f * height > 0f);
    }

    private static boolean crossesFade(float oldY, float y, int height, float top, float bottom) {
        return top <= 0f || bottom <= 0f || oldY < top || y < top
                || oldY + height > bottom || y + height > bottom;
    }

    private int firstVisibleRow(Object stack, List<?> children) throws IllegalAccessException {
        // The algorithm's "visibleChildren" includes rows above the viewport. A retained
        // scroll position must not give the first visible card row 23's delay/zero scale.
        if (scrollY.getInt(stack) <= 0 || actualHeight == null) return 0;
        float offset;
        try { offset = ((Number) topPadding.invoke(stack, NO_ARGS)).floatValue() - stackY.getFloat(stackAmbient.get(stack)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i), state = childState.get(child);
            if (state != null && (stateGone == null || !stateGone.getBoolean(state))
                    && stateY.getFloat(state) + offset + actualHeight.getInt(child) > 0f) return i;
        }
        return Math.max(0, children.size() - 1);
    }

    /** Temporarily replaces expansion transforms, keeping native scrolling/shelf/heads-up layout. */
    List<State> apply(Object stack) throws ReflectiveOperationException {
        if (nativeRows != null) {
            try { nativeRows.apply(stack); return null; }
            catch (LinkageError | ClassCastException failure) { disableNative(failure); }
        }
        if (!applied.isEmpty()) throw new IllegalStateException("Nested notification state application");
        List<?> children = children(stack);
        // Only animate actual rows; absent offscreen slots must not keep the frame clock alive.
        firstRow = firstVisibleRow(stack, children);
        model.setRowCount(children.size() - firstRow);
        float nativeStackY = stackY.getFloat(stackAmbient.get(stack));
        float fullStackY = ((Number) topPadding.invoke(stack, NO_ARGS)).floatValue();
        try {
            for (int i = firstRow; i < children.size(); i++) {
                Object child = children.get(i);
                if (((View) child).isPressed() || (Boolean) mustStay.invoke(child, NO_ARGS)) continue;
                Object target = childState.get(child);
                if (target == null) continue;
                int slot = applied.size();
                if (slot == pool.size()) pool.add(new State(this));
                State saved = pool.get(slot);
                saved.capture(target);
                applied.add(saved);
                int row = Math.min(i - firstRow, QsModel.MAX_ROWS - 1);
                // Replace Samsung's expansion-dependent mStackY component with the QS panel slide.
                float y = saved.y + fullStackY - nativeStackY
                        + model.translation.value + model.rowTranslation(row);
                stateY.setFloat(target, y);
                stateScaleX.setFloat(target, model.scale[row].value);
                stateScaleY.setFloat(target, model.scale[row].value);
                stateAlpha.setFloat(target, model.rowAlpha(row));
            }
            return applied;
        } catch (Throwable t) {
            restore(applied);
            throw t;
        }
    }

    static void restore(List<State> changes) throws IllegalAccessException {
        if (changes == null) return;
        for (int i = 0; i < changes.size(); i++) {
            State s = changes.get(i);
            s.owner.stateY.setFloat(s.target, s.y);
            s.owner.stateScaleX.setFloat(s.target, s.scaleX);
            s.owner.stateScaleY.setFloat(s.target, s.scaleY);
            s.owner.stateAlpha.setFloat(s.target, s.alpha);
            s.target = null;
        }
        changes.clear();
    }

    void restoreApplied(List<State> changes) throws IllegalAccessException {
        if (nativeRows != null) nativeRows.restore();
        restore(changes);
    }

    static final class State {
        final NotificationRows owner;
        Object target;
        float y, scaleX, scaleY, alpha;

        State(NotificationRows owner) { this.owner = owner; }

        void capture(Object target) throws IllegalAccessException {
            this.target = target;
            y = owner.stateY.getFloat(target);
            scaleX = owner.stateScaleX.getFloat(target);
            scaleY = owner.stateScaleY.getFloat(target);
            alpha = owner.stateAlpha.getFloat(target);
        }
    }
}
