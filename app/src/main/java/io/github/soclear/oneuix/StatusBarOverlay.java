package io.github.soclear.oneuix;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.Looper;
import android.util.TypedValue;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Keep legacy resource-based status-bar padding consistent with WindowInsets. */
@android.annotation.SuppressLint({"PrivateApi", "DiscouragedApi", "DiscouragedPrivateApi", "InternalInsetResource"})
@androidx.annotation.RequiresApi(34)
public final class StatusBarOverlay {
    private static final String NAME = BuildConfig.APPLICATION_ID.replace('.', '_') + "_DualStatusBar";

    public static void restart(Context context, boolean enabled) throws Exception {
        String apk = context.getApplicationInfo().sourceDir.replace("'", "'\\''");
        int user = android.os.Process.myUid() / 100000;
        // A fresh helper reads the original resource after disabling our previous override.
        String command = "cmd overlay disable --user " + user + " com.android.shell:" + NAME + " 2>/dev/null; "
            + "CLASSPATH='" + apk + "' /system/bin/app_process /system/bin "
            + StatusBarOverlay.class.getName() + " " + enabled + " " + user
            + "; oneuix_status=$?; killall com.android.systemui; exit $oneuix_status";
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Could not apply status bar resources");
            }
        } finally {
            process.destroy();
        }
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("BlockedPrivateApi") // UID-0 app_process has no app hidden-API restrictions.
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        OverlayManager manager = context.getSystemService(OverlayManager.class);
        Class<?> type = Class.forName("android.content.om.OverlayManagerTransaction$Builder");
        Object transaction = type.getConstructor().newInstance();
        OverlayIdentifier id = OverlayIdentifier.class.getConstructor(String.class, String.class)
            .newInstance("com.android.shell", NAME);
        if (Boolean.parseBoolean(args[0])) {
            var resources = context.createPackageContext("android", 0).getResources();
            int resource = resources.getIdentifier("status_bar_height_portrait", "dimen", "android");
            float height = resources.getDimensionPixelSize(resource) + Math.round(24 * resources.getDisplayMetrics().density);
            Class<?> builderType = Class.forName("android.content.om.FabricatedOverlay$Builder");
            Object builder = builderType.getConstructor(String.class, String.class, String.class)
                .newInstance("com.android.shell", NAME, "android");
            FabricatedOverlay overlay = (FabricatedOverlay) builderType.getMethod("build").invoke(builder);
            // The public dimension setter only exists since API 36.1; use the underlying typed entry.
            Class<?> entryType = Class.forName("android.os.FabricatedOverlayInternalEntry");
            Object entry = entryType.getConstructor().newInstance();
            entryType.getField("resourceName").set(entry, "android:dimen/status_bar_height_portrait");
            entryType.getField("dataType").setInt(entry, TypedValue.TYPE_DIMENSION);
            entryType.getField("data").setInt(entry, Math.round(height) << 8); // Integer pixels, radix 23p0.
            var internalField = FabricatedOverlay.class.getDeclaredField("mOverlay");
            internalField.setAccessible(true);
            Object internal = internalField.get(overlay);
            @SuppressWarnings("unchecked")
            List<Object> entries = (List<Object>) internal.getClass().getField("entries").get(internal);
            entries.add(entry);
            type.getMethod("registerFabricatedOverlay", FabricatedOverlay.class).invoke(transaction, overlay);
            type.getMethod("setEnabled", OverlayIdentifier.class, boolean.class, int.class)
                .invoke(transaction, id, true, Integer.parseInt(args[1]));
        } else {
            type.getMethod("unregisterFabricatedOverlay", OverlayIdentifier.class).invoke(transaction, id);
        }
        manager.commit((OverlayManagerTransaction) type.getMethod("build").invoke(transaction));
        System.exit(0);
    }
}
