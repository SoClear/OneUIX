package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

import io.github.libxposed.api.XposedModule;

/**
 * Legacy-API facade over libxposed. Only what ColorMotion needs is implemented:
 * hookMethod with before/after callbacks, and logging.
 */
public final class XposedBridge {
    private static volatile XposedModule module;

    private XposedBridge() { }

    /** Called once by the module entry before any hook is installed. */
    public static void init(XposedModule xposedModule) {
        module = xposedModule;
    }

    public static void log(String text) {
        XposedModule m = module;
        // A null tag lets the framework pick the default tag its log filter accepts.
        if (m != null) m.log(Log.INFO, null, text);
    }

    public static void log(Throwable throwable) {
        XposedModule m = module;
        if (m != null) m.log(Log.ERROR, null, "ColorMotion: unexpected exception", throwable);
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        XposedModule m = module;
        if (m == null) throw new IllegalStateException("XposedBridge.init was not called");
        if (!(hookMethod instanceof Executable)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + hookMethod);
        }
        final Executable executable = (Executable) hookMethod;
        final XC_MethodHook.Unhook unhook = callback.new Unhook(hookMethod);

        m.hook(executable).intercept(chain -> {
            if (unhook.disabled) {
                try {
                    return chain.proceed();
                } catch (Throwable t) {
                    throw sneakyThrow(t);
                }
            }

            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.method = executable;
            param.thisObject = chain.getThisObject();
            param.args = chain.getArgs().toArray();

            try {
                callback.callBefore(param);
            } catch (Throwable t) {
                log(t);
                param.resetEarlyReturn();
            }

            if (!param.returnEarly) {
                try {
                    param.setOriginalResult(chain.proceed(param.args));
                } catch (Throwable t) {
                    param.setOriginalThrowable(t);
                }
            }

            Object previousResult = param.getResult();
            Throwable previousThrowable = param.getThrowable();
            try {
                callback.callAfter(param);
            } catch (Throwable t) {
                log(t);
                if (previousThrowable != null) param.setOriginalThrowable(previousThrowable);
                else param.setOriginalResult(previousResult);
            }

            if (param.hasThrowable()) throw sneakyThrow(param.getThrowable());
            return param.getResult();
        });
        return unhook;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
