package dev.colormotion;

import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;

/** Bootstrap-only connection to the typed notification adapter, with reused pose/command buffers. */
final class NativeNotifications {
    private final QsModel model;
    private final Object[] stack = new Object[1];
    private final float[] poses = new float[QsModel.MAX_ROWS * 3 + 1];
    private final int[] command = new int[1];
    private final Runnable driver;
    private final IntSupplier count;
    private final BooleanSupplier layoutPending;
    private boolean applied;

    NativeNotifications(NativeLayers bindings, QsModel model) throws ReflectiveOperationException {
        this.model = model;
        driver = bindings.notifications(stack, poses, command);
        count = (IntSupplier) driver;
        layoutPending = (BooleanSupplier) driver;
    }

    void prepare(Object owner) {
        stack[0] = owner;
        try { model.setRowCount(count.getAsInt()); }
        finally { stack[0] = null; }
    }

    private void updatePoses() {
        for (int row = 0; row < model.rowCount; row++) {
            int p = row * 3;
            poses[p] = model.rowAlpha(row);
            poses[p + 1] = model.scale[row].value;
            poses[p + 2] = model.rowTranslation(row);
        }
        poses[poses.length - 1] = model.translation.value;
    }

    void render(Object owner) {
        render(owner, false);
    }

    /** See NotificationRows.renderPrepared: prepare and render share one synchronous frame. */
    void renderPrepared(Object owner) {
        render(owner, true);
    }

    private void render(Object owner, boolean prepared) {
        stack[0] = owner;
        try {
            if (layoutPending.getAsBoolean()) return;
            if (!prepared) model.setRowCount(count.getAsInt());
            updatePoses();
            command[0] = 0;
            driver.run();
        } finally { stack[0] = null; }
    }

    void apply(Object owner) {
        if (applied) throw new IllegalStateException("Nested notification state application");
        stack[0] = owner;
        try {
            model.setRowCount(count.getAsInt());
            updatePoses();
            command[0] = 1;
            driver.run();
            applied = true;
        } finally { stack[0] = null; }
    }

    void restore() {
        if (!applied) return;
        command[0] = 2;
        try { driver.run(); }
        finally { applied = false; }
    }
}
