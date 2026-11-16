package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.HashMap;

/**
 * Minimal subset of the legacy (API 82) callback type, implemented on top of libxposed by
 * {@link XposedBridge}. It exists so ColorMotion's hooks can run unchanged inside OneUIX.
 */
public abstract class XC_MethodHook {
    public XC_MethodHook() { }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }

    void callBefore(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    void callAfter(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;
        boolean returnEarly;
        private HashMap<String, Object> extras;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }

        public Object getObjectExtra(String key) {
            return extras == null ? null : extras.get(key);
        }

        public void setObjectExtra(String key, Object value) {
            if (extras == null) extras = new HashMap<>();
            extras.put(key, value);
        }

        /** Result of the original method; unlike setResult it does not skip anything. */
        void setOriginalResult(Object result) {
            this.result = result;
            this.throwable = null;
        }

        void setOriginalThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
        }

        void resetEarlyReturn() {
            this.result = null;
            this.throwable = null;
            this.returnEarly = false;
        }
    }

    /** Handle for one registered hook; unhooking turns the hook into a pass-through. */
    public final class Unhook {
        private final Member hookedMethod;
        volatile boolean disabled;

        Unhook(Member hookedMethod) {
            this.hookedMethod = hookedMethod;
        }

        public Member getHookedMethod() {
            return hookedMethod;
        }

        public XC_MethodHook getCallback() {
            return XC_MethodHook.this;
        }

        public void unhook() {
            disabled = true;
        }
    }
}
