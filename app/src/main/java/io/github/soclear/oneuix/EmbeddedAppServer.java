package io.github.soclear.oneuix;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/** UID-0 app_process owns only this session's virtual display. Target apps keep their own UIDs. */
@android.annotation.SuppressLint({"BlockedPrivateApi", "PrivateApi", "DiscouragedPrivateApi", "WrongConstant"})
public final class EmbeddedAppServer extends Binder implements DisplayManager.DisplayListener {
    private static final String PROTOCOL = "io.github.soclear.oneuix.EmbeddedApp";
    private static final String TAG = "OneUIX.EmbeddedApp";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private final int clientUid;
    private final DisplayManager displays;
    private final Object windows, activities, input;
    private final Method inject, setDisplay;
    private IBinder client;
    private VirtualDisplay display;
    private volatile int id = -1;
    private volatile boolean attached;
    private volatile Matrix inputTransform = new Matrix();
    private int width, height;
    private final Runnable checkTasks = () -> run(() -> {
        if (attached && id != -1 && tasks().stream().noneMatch(this::owns)) notifyClient(3, null);
    });

    @SuppressWarnings("JavaReflectionMemberAccess") // These hidden framework members are available in app_process.
    private EmbeddedAppServer(Context base, int uid) throws Exception {
        context = new ContextWrapper(base) {
            @NonNull @Override public String getPackageName() { return "com.android.shell"; }
            @NonNull @Override public String getOpPackageName() { return "com.android.shell"; }
        };
        clientUid = uid;
        displays = DisplayManager.class.getConstructor(Context.class).newInstance(context);
        windows = call(Class.forName("android.view.WindowManagerGlobal"), "getWindowManagerService");
        activities = call(Class.forName("android.app.ActivityTaskManager"), "getService");
        Class<?> inputClass;
        try { inputClass = Class.forName("android.hardware.input.InputManagerGlobal"); }
        catch (ClassNotFoundException ignored) { inputClass = Class.forName("android.hardware.input.InputManager"); }
        input = call(inputClass, "getInstance");
        inject = inputClass.getMethod("injectInputEvent", InputEvent.class, int.class);
        setDisplay = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
        TaskStackListener taskListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                main.removeCallbacks(checkTasks);
                main.postDelayed(checkTasks, 300);
            }
        };
        call(activities, "registerTaskStackListener", taskListener);
        displays.registerDisplayListener(this, main);
    }

    @SuppressWarnings("deprecation") // app_process has no framework-created main looper.
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        // Android 15+ normally initializes this in bindApplication, which app_process skips.
        try {
            Class<?> memory = Class.forName("com.android.internal.os.ApplicationSharedMemory");
            var instance = memory.getDeclaredField("sInstance"); instance.setAccessible(true);
            if (instance.get(null) == null) call(memory, "setInstance", call(memory, "create"));
        } catch (ClassNotFoundException ignored) { /* Android 13/14. */ }
        Object thread = call(Class.forName("android.app.ActivityThread"), "systemMain");
        EmbeddedAppServer server = new EmbeddedAppServer((Context) call(thread, "getSystemContext"), Integer.parseInt(args[1]));
        Bundle extras = new Bundle(); extras.putBinder("server", server);
        Intent intent = new Intent(args[0] + ".EMBEDDED_ROOT_READY")
                .setComponent(new ComponentName(args[0], "io.github.soclear.oneuix.MediaPageService"))
                .putExtra("nonce", args[2]).putExtras(extras);
        Object result = call(call(ActivityManager.class, "getService"), "startService",
                null, intent, null, false, "com.android.shell", null, 0);
        if (!(result instanceof ComponentName)) throw new IllegalStateException("Cannot connect to OneUIX");
        server.main.postDelayed(() -> { if (server.client == null) server.close(); }, 15000);
        Looper.loop();
    }

    @Override protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) {
        if (Binder.getCallingUid() != clientUid) throw new SecurityException("Wrong client UID");
        data.enforceInterface(PROTOCOL);
        switch (code) {
            case 1 -> {
                IBinder owner = Objects.requireNonNull(data.readStrongBinder());
                main.post(() -> run(() -> {
                    if (client != null) throw new IllegalStateException("Already connected");
                    client = owner; client.linkToDeath(() -> main.post(this::close), 0);
                }));
            }
            case 2 -> {
                Surface surface = data.readTypedObject(Surface.CREATOR);
                int w = data.readInt(), h = data.readInt(), density = data.readInt();
                float rate = data.readFloat();
                ComponentName component = data.readTypedObject(ComponentName.CREATOR);
                main.post(() -> run(() -> {
                    try {
                        attached = surface != null;
                        if (attached) attach(surface, w, h, density, rate, component);
                        else if (display != null) {
                            display.setSurface(null);
                            call(windows, "moveDisplayToTop", 0, "OneUIX home");
                        }
                    } finally { if (surface != null) surface.release(); }
                }));
            }
            case 3 -> {
                MotionEvent event = Objects.requireNonNull(data.readTypedObject(MotionEvent.CREATOR));
                // No main-thread queue hop per input sample. Clear the app's incoming Binder identity.
                long identity = Binder.clearCallingIdentity();
                try { run(() -> {
                    if (!attached || id == -1) return;
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) focus();
                    event.transform(inputTransform); setDisplay.invoke(event, id);
                    if (!Boolean.TRUE.equals(inject.invoke(input, event, 0)))
                        throw new IllegalStateException("Input injection failed");
                    if (event.getActionMasked() == MotionEvent.ACTION_UP)
                        main.postDelayed(() -> run(() -> { if (attached) focus(); }), 40);
                }); } finally { event.recycle(); Binder.restoreCallingIdentity(identity); }
            }
            case 4 -> main.post(() -> run(() -> {
                if (!attached) return;
                focus();
                main.postDelayed(() -> run(() -> {
                    if (!attached) return;
                    long now = android.os.SystemClock.uptimeMillis();
                    for (int action = 0; action <= 1; action++) {
                        var event = new android.view.KeyEvent(now, now, action, android.view.KeyEvent.KEYCODE_BACK,
                                0, 0, -1, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD);
                        setDisplay.invoke(event, id); inject.invoke(input, event, 2);
                    }
                }), 60);
            }));
            case 5 -> main.post(this::close);
            default -> { return false; }
        }
        return true;
    }

    private void attach(Surface surface, int w, int h, int density, float rate, ComponentName component) throws Exception {
        if (display == null) {
            // PUBLIC | OWN_CONTENT_ONLY | SUPPORTS_TOUCH | ROTATES_WITH_CONTENT |
            // DESTROY_CONTENT_ON_REMOVAL | TRUSTED | OWN_DISPLAY_GROUP. No SECURE/ALWAYS_UNLOCKED.
            int flags = 1 | 8 | 64 | 128 | 256 | 1024 | 2048;
            if (Build.VERSION.SDK_INT >= 34) {
                // Physical touches focus display 0. Keep the embedded app focused throughout the gesture.
                flags |= 16384; // OWN_FOCUS
                display = displays.createVirtualDisplay(new VirtualDisplayConfig.Builder("OneUIX app page", w, h, density)
                        .setSurface(surface).setFlags(flags).setRequestedRefreshRate(rate).build());
            } else display = displays.createVirtualDisplay("OneUIX app page", w, h, density, surface, flags);
            if (display == null) throw new IllegalStateException("Virtual display unavailable");
            id = display.getDisplay().getDisplayId();
            call(windows, "setDisplayImePolicy", id, 0); // LOCAL: keyboard renders into this display.
            call(windows, "setFixedToUserRotation", id, 1); // Virtual displays default to a fixed portrait rotation.
            call(windows, "setIgnoreOrientationRequest", id, false);
        } else {
            if (width != w || height != h) display.resize(w, h, density);
            display.setSurface(surface);
        }
        width = w; height = h;
        if (tasks().stream().noneMatch(this::owns)) launch(component);
        onDisplayChanged(id);
    }

    private void focus() throws Exception { call(windows, "moveDisplayToTop", id, "OneUIX input"); }

    @SuppressWarnings("deprecation") // UID 0 can still query all tasks; ordinary app restrictions do not apply.
    private List<ActivityManager.RunningTaskInfo> tasks() {
        return Objects.requireNonNull(context.getSystemService(ActivityManager.class)).getRunningTasks(Integer.MAX_VALUE);
    }

    // displayId is present at runtime but omitted from the public SDK.
    private boolean owns(ActivityManager.RunningTaskInfo task) {
        try { return task.getClass().getField("displayId").getInt(task) == id && task.numActivities > 0; }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void launch(ComponentName component) throws Exception {
        ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
        if (info.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK || info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
            for (var task : tasks()) {
                boolean samePackage = (task.baseActivity != null && component.getPackageName().equals(task.baseActivity.getPackageName())) ||
                        (task.topActivity != null && component.getPackageName().equals(task.topActivity.getPackageName()));
                if (samePackage && !owns(task)) {
                    call(activities, "startActivityFromRecents", task.taskId,
                            ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle());
                    return;
                }
            }
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        int result = (Integer) call(call(ActivityManager.class, "getService"), "startActivityAsUser",
                null, "com.android.shell", intent, null, null, null, 0, 0, null,
                ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle(), 0);
        if (result < 0) throw new IllegalStateException("Activity launch failed: " + result);
    }

    @Override public void onDisplayChanged(int changed) {
        if (changed != id || display == null) return;
        int rotation = display.getDisplay().getRotation();
        Matrix matrix = new Matrix(); matrix.setRotate(-90f * rotation);
        switch (rotation) {
            case Surface.ROTATION_0 -> { }
            case Surface.ROTATION_90 -> matrix.postTranslate(0, width);
            case Surface.ROTATION_180 -> matrix.postTranslate(width, height);
            case Surface.ROTATION_270 -> matrix.postTranslate(height, 0);
        }
        inputTransform = matrix;
        notifyClient(1, Integer.toString(rotation));
    }
    @Override public void onDisplayAdded(int changed) { }
    @Override public void onDisplayRemoved(int changed) { }

    private interface Action { void run() throws Exception; }
    private void run(Action action) {
        try { action.run(); }
        catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Log.e(TAG, "Root helper failed", cause); main.post(() -> notifyClient(2, cause.toString()));
        }
    }

    /** Named framework calls only; input hot-path methods are cached separately. */
    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(target instanceof Class<?> ? null : target, args);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private void notifyClient(int code, String message) {
        if (client == null) return;
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInterfaceToken(PROTOCOL); parcel.writeString(message);
            client.transact(code, parcel, null, IBinder.FLAG_ONEWAY);
        } catch (Exception error) { close(); }
        finally { parcel.recycle(); }
    }

    private void close() {
        if (display != null) {
            try { for (var task : tasks()) if (owns(task)) call(activities, "removeTask", task.taskId); }
            catch (Exception error) { Log.e(TAG, "Cannot remove embedded tasks", error); }
            display.release();
        }
        System.exit(0);
    }
}
