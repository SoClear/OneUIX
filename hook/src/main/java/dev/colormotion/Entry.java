package dev.colormotion;

import de.robv.android.xposed.XposedBridge;

/**
 * Entry point used by OneUIX. The original ColorMotion module started from an
 * IXposedHookLoadPackage; OneUIX owns the module entry and calls {@link #install} for SystemUI.
 */
public final class Entry {
    static final String TAG = "ColorMotion";

    private static boolean installed;

    private Entry() { }

    public static synchronized void install(ClassLoader systemUiLoader) {
        if (installed) return;
        installed = true;
        try {
            new ShadeMotion(systemUiLoader).install();
            XposedBridge.log(TAG + ": hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": install failed");
            XposedBridge.log(t);
        }
    }
}
