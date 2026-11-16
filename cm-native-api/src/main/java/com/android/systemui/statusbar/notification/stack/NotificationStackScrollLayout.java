package com.android.systemui.statusbar.notification.stack;
import android.view.View;
/** Compile-only captured ABI. No stack/view implementation is packaged. */
public class NotificationStackScrollLayout extends View {
    public StackScrollAlgorithm mStackScrollAlgorithm;
    public AmbientState mAmbientState;
    public boolean mChildrenUpdateRequested;
    public int mOwnScrollY;
    public NotificationStackScrollLayout() { super(null); throw new UnsupportedOperationException("ABI stub"); }
    public int getTopPadding() { throw new UnsupportedOperationException("ABI stub"); }
}
