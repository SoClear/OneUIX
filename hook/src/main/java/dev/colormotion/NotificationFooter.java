package dev.colormotion;

import android.view.View;
import java.lang.reflect.Field;

/** Samsung's bottom button shelf is deliberately absent from the animated visibleChildren list. */
final class NotificationFooter {
    private final Field shelf, state, y, scaleX, scaleY, alpha, hidden, gone, pending, shown;
    private Object saved;
    private float savedY, savedX, savedScaleY, savedAlpha;

    NotificationFooter(Class<?> stackClass, Class<?> expandableClass) throws ReflectiveOperationException {
        shelf = stackClass.getField("mShelf");
        shown = shelf.getType().getField("mShowNotificationShelf");
        pending = stackClass.getField("mChildrenUpdateRequested");
        state = expandableClass.getField("mViewState");
        Class<?> stateClass = state.getType();
        y = stateClass.getField("mYTranslation");
        scaleX = stateClass.getField("mScaleX");
        scaleY = stateClass.getField("mScaleY");
        alpha = stateClass.getField("mAlpha");
        hidden = stateClass.getField("hidden");
        gone = stateClass.getField("gone");
        for (Field f : new Field[] {shelf, state, y, scaleX, scaleY, alpha, hidden, gone, pending, shown})
            f.setAccessible(true);
    }

    boolean isShelf(Object stack, Object view) throws IllegalAccessException {
        return shelf.get(stack) == view;
    }

    void apply(Object stack, QsModel model) throws ReflectiveOperationException {
        if (saved != null) throw new IllegalStateException("Nested footer state application");
        View view = (View) shelf.get(stack);
        if (view == null || view.isPressed() || !shown.getBoolean(view)) return;
        Object target = state.get(view);
        if (target == null || gone.getBoolean(target)) return;
        savedY = y.getFloat(target);
        savedX = scaleX.getFloat(target);
        savedScaleY = scaleY.getFloat(target);
        savedAlpha = alpha.getFloat(target);
        saved = target;
        // Shelf Y is already anchored to the screen bottom. It must not receive the cards'
        // top-padding/stack-Y compensation or inherit a partially expanded card's stale scale.
        y.setFloat(target, savedY + model.footerTranslation());
        scaleX.setFloat(target, model.footerScale());
        scaleY.setFloat(target, model.footerScale());
        alpha.setFloat(target, model.footerOpacity());
    }

    void restore() throws IllegalAccessException {
        if (saved == null) return;
        Object target = saved;
        saved = null;
        y.setFloat(target, savedY);
        scaleX.setFloat(target, savedX);
        scaleY.setFloat(target, savedScaleY);
        alpha.setFloat(target, savedAlpha);
    }

    void render(Object stack, QsModel model, boolean afterNative) throws ReflectiveOperationException {
        if (!afterNative && pending.getBoolean(stack)) return;
        View view = (View) shelf.get(stack);
        if (view == null || view.isPressed() || !shown.getBoolean(view)) return;
        Object target = state.get(view);
        if (target == null || gone.getBoolean(target)) return;
        float a = model.footerOpacity(), s = model.footerScale();
        float position = (saved == target ? savedY : y.getFloat(target)) + model.footerTranslation();
        if (view.getTranslationY() != position) view.setTranslationY(position);
        if (view.getScaleX() != s) view.setScaleX(s);
        if (view.getScaleY() != s) view.setScaleY(s);
        if (view.getAlpha() != a) view.setAlpha(a);
        int visibility = hidden.getBoolean(target) || a == 0f ? View.INVISIBLE : View.VISIBLE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }
}
