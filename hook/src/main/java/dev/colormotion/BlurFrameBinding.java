package dev.colormotion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Use Samsung's full frame operation on either legacy or refactored blur controllers. */
final class BlurFrameBinding {
    private static final Object[] NO_ARGS = new Object[0];
    final Method frame;
    final Object skippedResult;
    private final Field callback;
    private final Method boundReceiver;
    private final Object[] args = new Object[1];
    private Object lastCallback, lastController;

    BlurFrameBinding(Class<?> controller, ClassLoader cl) throws ReflectiveOperationException {
        Method legacy;
        try { legacy = controller.getDeclaredMethod("access$doFrame", controller); }
        catch (NoSuchMethodException absent) { legacy = null; }
        if (legacy != null) {
            frame = legacy;
            callback = null;
            boundReceiver = null;
            skippedResult = null;
        } else {
            callback = controller.getField("updateBlurCallback");
            Class<?> implementation = XposedHelpers.findClass(controller.getName() + "$updateBlurCallback$1", cl);
            frame = implementation.getDeclaredMethod("invoke", Object.class);
            boundReceiver = implementation.getMethod("getBoundReceiver");
            boundReceiver.setAccessible(true);
            Class<?> unit = XposedHelpers.findClass("kotlin.Unit", cl);
            skippedResult = unit.getField("INSTANCE").get(null);
            callback.setAccessible(true);
        }
        frame.setAccessible(true);
    }

    Object controller(XC_MethodHook.MethodHookParam param) throws ReflectiveOperationException {
        if (callback == null) return param.args[0];
        // Kotlin function references have an immutable receiver. Native rescheduling reuses
        // the same reference; a new controller/reference must resolve its own receiver.
        if (param.thisObject != lastCallback) {
            Object receiver = boundReceiver.invoke(param.thisObject, NO_ARGS);
            lastCallback = param.thisObject;
            lastController = receiver;
        }
        return lastController;
    }

    void request(Object controller) throws ReflectiveOperationException {
        if (callback == null) {
            args[0] = controller;
            try { frame.invoke(null, args); }
            finally { args[0] = null; }
        } else {
            args[0] = Long.valueOf(0L);
            frame.invoke(callback.get(controller), args);
        }
    }
}
