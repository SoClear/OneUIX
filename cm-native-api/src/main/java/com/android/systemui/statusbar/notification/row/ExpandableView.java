package com.android.systemui.statusbar.notification.row;
import android.view.View;
import com.android.systemui.statusbar.notification.stack.ExpandableViewState;
/** Compile-only captured ABI; all real instances come from SystemUI. */
public class ExpandableView extends View {
    public int mActualHeight = 160;
    public ExpandableViewState mViewState;
    public boolean mWillBeGone;
    public ExpandableView() { super(null); throw new UnsupportedOperationException("ABI stub"); }
    public boolean mustStayOnScreen() { throw new UnsupportedOperationException("ABI stub"); }
}
