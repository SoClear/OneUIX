package androidx.compose.runtime.snapshots;

/** Compile-only Samsung runtime ABI. The bridge resolves the real types from SystemUI. */
public abstract class Snapshot {
    public static final Companion Companion = null;
    public static final class Companion {
        public static MutableSnapshot takeMutableSnapshot$default(Companion self) {
            throw new UnsupportedOperationException("ABI stub");
        }
    }
    public Snapshot makeCurrent() { throw new UnsupportedOperationException("ABI stub"); }
    public static void restoreCurrent(Snapshot previous) { throw new UnsupportedOperationException("ABI stub"); }
    public void dispose() { throw new UnsupportedOperationException("ABI stub"); }
}
