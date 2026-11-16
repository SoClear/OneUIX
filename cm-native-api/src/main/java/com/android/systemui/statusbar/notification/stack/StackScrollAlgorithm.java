package com.android.systemui.statusbar.notification.stack;
import java.util.ArrayList;
import com.android.systemui.statusbar.notification.row.ExpandableView;
public class StackScrollAlgorithm {
    public StackScrollAlgorithmState mTempAlgorithmState;
    public static class StackScrollAlgorithmState { public ArrayList<ExpandableView> visibleChildren; }
}
