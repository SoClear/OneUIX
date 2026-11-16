package dev.colormotion.nativebridge;

import androidx.compose.ui.graphics.ReusableGraphicsLayerScope;

/** Cold binding check before any bridge is handed to Compose; failure selects reflective binding. */
public final class Probe {
    public static void verify(Object state) {
        ReusableGraphicsLayerScope scope = new ReusableGraphicsLayerScope();
        float[] pose = {0.6f, 0.8f, 24f};
        LayerBlock block = new LayerBlock(state, pose);
        block.invoke(scope);
        if (scope.alpha != pose[0] || scope.scaleX != pose[1] || scope.scaleY != pose[1]
                || scope.translationY != pose[2] || scope.compositingStrategy != 2
                || (scope.mutatedFields & 32791) != 32791) throw new IllegalStateException("Layer ABI mismatch");
        new LayerBatch(new Runnable[] {block}, new int[] {1}).run();
    }
}
