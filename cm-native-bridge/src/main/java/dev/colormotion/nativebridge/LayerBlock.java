package dev.colormotion.nativebridge;

import androidx.compose.runtime.MutableState;
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Loaded with SystemUI as parent: native interfaces and fields resolve to its own Compose copy. */
public final class LayerBlock implements Function1<Object, Unit>, Runnable {
    private final MutableState<Object> signal;
    private final float[] pose;

    @SuppressWarnings("unchecked")
    public LayerBlock(Object signal, float[] pose) {
        this.signal = (MutableState<Object>) signal;
        this.pose = pose;
    }

    @Override public Unit invoke(Object target) {
        signal.getValue(); // Subscribe this layer, without a proxy or reflected call.
        ReusableGraphicsLayerScope scope = (ReusableGraphicsLayerScope) target;
        scope.alpha = pose[0];
        scope.scaleX = pose[1];
        scope.scaleY = pose[1];
        scope.translationY = pose[2];
        scope.compositingStrategy = 2; // ModulateAlpha: no implicit bounds around moving descendants.
        // Compose resets this scope to identity and merges this mask with the layer's
        // previous mask. A property returning to identity is therefore still restored once.
        int changed = 32768;
        if (pose[0] != 1f) changed |= 4;
        if (pose[1] != 1f) changed |= 1 | 2;
        if (pose[2] != 0f) changed |= 16;
        scope.mutatedFields |= changed;
        return Unit.INSTANCE;
    }

    @Override public void run() {
        // Read the committed/current-snapshot value, so a failed transaction can retry safely.
        signal.setValue(signal.getValue() == Boolean.TRUE ? Boolean.FALSE : Boolean.TRUE);
    }
}
