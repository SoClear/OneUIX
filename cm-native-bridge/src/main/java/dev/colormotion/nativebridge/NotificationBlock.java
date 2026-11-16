package dev.colormotion.nativebridge;

import android.view.View;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.stack.ExpandableViewState;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.notification.stack.ViewState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;

/** Typed render/state-consumer adapter. Shared buffers use bootstrap types across class loaders. */
public final class NotificationBlock implements Runnable, IntSupplier, BooleanSupplier {
    private final Object[] owner;
    private final float[] poses;
    private final int[] command;
    private final ArrayList<Saved> pool = new ArrayList<>();
    private int savedCount;
    private int firstRow;
    private float lastFadeTop=Float.NaN, lastFadeBottom=Float.NaN;

    public NotificationBlock(Object[] owner, float[] poses, int[] command) {
        this.owner = owner;
        this.poses = poses;
        this.command = command;
    }

    private NotificationStackScrollLayout stack() { return (NotificationStackScrollLayout) owner[0]; }
    private ArrayList<ExpandableView> children(NotificationStackScrollLayout stack) {
        return stack.mStackScrollAlgorithm.mTempAlgorithmState.visibleChildren;
    }
    @Override public int getAsInt() {
        NotificationStackScrollLayout stack = stack();
        ArrayList<ExpandableView> children = children(stack);
        firstRow = firstVisibleRow(stack, children);
        return children.size() - firstRow;
    }

    private int firstVisibleRow(NotificationStackScrollLayout stack, ArrayList<ExpandableView> children) {
        if (stack.mOwnScrollY <= 0) return 0;
        float offset = stack.getTopPadding() - stack.mAmbientState.mStackY;
        for (int i = 0; i < children.size(); i++) {
            ExpandableView child = children.get(i);
            ExpandableViewState state = child.mViewState;
            if (state != null && !state.gone && state.mYTranslation + offset + child.mActualHeight > 0f) return i;
        }
        return Math.max(0, children.size() - 1);
    }
    @Override public boolean getAsBoolean() { return stack().mChildrenUpdateRequested; }

    @Override public void run() {
        if (command[0] == 2) { restore(); return; }
        NotificationStackScrollLayout stack = stack();
        if (command[0] == 0) render(stack);
        else if (command[0] == 1) apply(stack);
        else throw new IllegalArgumentException("Unknown notification command");
    }

    private int poseIndex(int row) { return Math.min(row, (poses.length - 1) / 3 - 1) * 3; }
    private float root() { return poses[poses.length - 1]; }

    private void render(NotificationStackScrollLayout stack) {
        if (stack.mChildrenUpdateRequested) return;
        ArrayList<ExpandableView> children = children(stack);
        float fadeTop = stack.mAmbientState.mBlurTopFadeoutEnd;
        float fadeBottom = stack.mAmbientState.mBlurBottomFadeoutStart;
        boolean fadeChanged = fadeTop != lastFadeTop || fadeBottom != lastFadeBottom;
        float stackOffset = stack.getTopPadding() - stack.mAmbientState.mStackY + root();
        int viewport = stack.getHeight();
        for (int i = firstRow; i < children.size(); i++) {
            ExpandableView child = children.get(i);
            if (child.isPressed() || child.mustStayOnScreen() || child.mWillBeGone) continue;
            ExpandableViewState state = child.mViewState;
            if (state == null || state.gone || ViewState.isAnimating(child)) continue;
            int p = poseIndex(i - firstRow);
            float alpha = poses[p], scale = poses[p + 1];
            float y = state.mYTranslation + stackOffset + poses[p + 2];
            float previousY = child.getTranslationY();
            if (!onStack(y, child.mActualHeight, viewport) && !onStack(previousY, child.mActualHeight, viewport)) {
                // Still below/above the stack: its pose cannot be seen. Each write invalidates the
                // tree and syncs another RenderNode. The first on-stack frame writes it again.
                if (child instanceof ExpandableNotificationRow && ((ExpandableNotificationRow) child).mIsFaded)
                    ((ExpandableNotificationRow) child).setNotificationFaded(false);
                continue;
            }
            if (previousY != y) child.setTranslationY(y);
            if (child.getScaleX() != scale) child.setScaleX(scale);
            if (child.getScaleY() != scale) child.setScaleY(scale);
            boolean hidden = state.hidden || alpha == 0f;
            // Fading gives each card's icons/buttons their own hardware layers. Cards below the
            // stack draw nothing, so creating and rendering their layers is pure GPU cost.
            boolean nextFaded = !hidden && alpha != 1f && onStack(y, child.mActualHeight, viewport);
            if (child instanceof ExpandableNotificationRow) {
                ExpandableNotificationRow row = (ExpandableNotificationRow) child;
                if (row.mIsFaded != nextFaded) row.setNotificationFaded(nextFaded);
            } else {
                int layer = nextFaded && child.hasOverlappingRendering() ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
                if (child.getLayerType() != layer) child.setLayerType(layer, null);
            }
            if (child.getAlpha() != alpha) child.setAlpha(alpha);
            int visibility = hidden ? View.INVISIBLE : View.VISIBLE;
            int previousVisibility = child.getVisibility();
            boolean becameVisible = visibility == View.VISIBLE && previousVisibility != View.VISIBLE;
            if (previousVisibility != visibility) child.setVisibility(visibility);
            // Do not update blur drawables for cards hidden by the distance/fade gate. On
            // reentry a forced refresh also clears masks retained during invisible movement.
            if (!hidden && (previousY != y || fadeChanged || becameVisible) && child instanceof ExpandableNotificationRow)
                refreshGradient((ExpandableNotificationRow) child, previousY, y, fadeTop, fadeBottom,
                        fadeChanged, becameVisible, viewport);
        }
        lastFadeTop = fadeTop;
        lastFadeBottom = fadeBottom;
    }

    /** A full card of margin covers entrance scale; native layout passes refresh every card. */
    private static boolean onStack(float y, int height, int viewport) {
        return viewport <= 0 || (y < viewport + height && y + 2f * height > 0f);
    }

    private void refreshGradient(ExpandableNotificationRow row, float oldY, float y, float top,
            float bottom, boolean changed, boolean force, int viewport) {
        int height = row.mActualHeight;
        // Cards below the stack cross the bottom fade band on every moving frame, yet draw
        // nothing. With many notifications this was most of the render cost. A full card of
        // margin covers entrance scale; native layout passes refresh every card's gradient.
        if (!onStack(y, height, viewport)) return;
        boolean wasEdge = changed && (Float.isNaN(lastFadeTop) || lastFadeTop <= 0f || lastFadeBottom <= 0f
                || oldY < lastFadeTop || y < lastFadeTop || oldY + height > lastFadeBottom || y + height > lastFadeBottom);
        if (height <= 0 || !(force || wasEdge || top <= 0f || bottom <= 0f || oldY < top || y < top
                || oldY + height > bottom || y + height > bottom)) return;
        row.updateBlurBackgroundGradient();
        List<?> attached = row.getAttachedChildren();
        if (attached == null) return;
        for (int i = 0; i < attached.size(); i++) {
            ExpandableNotificationRow child = (ExpandableNotificationRow) attached.get(i);
            if (child.getVisibility() == View.VISIBLE && child.mActualHeight > 0)
                child.updateBlurBackgroundGradient();
        }
    }

    private void apply(NotificationStackScrollLayout stack) {
        if (savedCount != 0) throw new IllegalStateException("Nested notification state application");
        ArrayList<ExpandableView> children = children(stack);
        float nativeStackY = stack.mAmbientState.mStackY;
        float fullStackY = stack.getTopPadding();
        try {
            for (int i = firstRow; i < children.size(); i++) {
                ExpandableView child = children.get(i);
                if (child.isPressed() || child.mustStayOnScreen()) continue;
                ExpandableViewState state = child.mViewState;
                if (state == null) continue;
                if (savedCount == pool.size()) pool.add(new Saved());
                Saved saved = pool.get(savedCount);
                saved.capture(state);
                savedCount++;
                int p = poseIndex(i - firstRow);
                // Keep the original expression order for float-identical compensation.
                state.mYTranslation = saved.y + fullStackY - nativeStackY + root() + poses[p + 2];
                state.mScaleX = poses[p + 1];
                state.mScaleY = poses[p + 1];
                state.mAlpha = poses[p];
            }
        } catch (Throwable failure) {
            restore();
            throw failure;
        }
    }

    private void restore() {
        for (int i = 0; i < savedCount; i++) {
            Saved saved = pool.get(i);
            ExpandableViewState state = saved.target;
            state.mYTranslation = saved.y;
            state.mScaleX = saved.sx;
            state.mScaleY = saved.sy;
            state.mAlpha = saved.alpha;
            saved.target = null;
        }
        savedCount = 0;
    }

    private static final class Saved {
        // Package constructor avoids a synthetic D8 nest-access marker class.
        Saved() {}
        ExpandableViewState target;
        float y, sx, sy, alpha;
        void capture(ExpandableViewState state) {
            y = state.mYTranslation;
            sx = state.mScaleX;
            sy = state.mScaleY;
            alpha = state.mAlpha;
            target = state;
        }
    }
}
