package dev.colormotion.nativebridge;

import androidx.compose.runtime.snapshots.MutableSnapshot;
import androidx.compose.runtime.snapshots.Snapshot;

/** One synchronous snapshot commit for all changed rows, before the current frame is drawn. */
public final class LayerBatch implements Runnable {
    private final Runnable[] pending;
    private final int[] count;

    public LayerBatch(Runnable[] pending, int[] count) {
        this.pending = pending;
        this.count = count;
    }

    @Override public void run() {
        int size = count[0];
        if (size == 0) return;
        MutableSnapshot snapshot = Snapshot.Companion.takeMutableSnapshot$default(Snapshot.Companion);
        try {
            Snapshot previous = snapshot.makeCurrent();
            try {
                for (int i = 0; i < size; i++) pending[i].run();
            } finally {
                Snapshot.restoreCurrent(previous);
            }
            snapshot.apply().check();
            count[0] = 0; // Keep the batch queued if a write or commit failed.
        } finally {
            snapshot.dispose();
        }
    }
}
