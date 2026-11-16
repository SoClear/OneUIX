package dev.colormotion;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XposedHelpers;

/** Correct the native fade's top discontinuity while retaining native rendering and bottom masks. */
final class NotificationEdgeFade {
    private static final Object[] NO_ARGS = new Object[0];
    private final Field drawing, shader, topStart, topEnd, bottomStart;
    private final Field ambient, scrim, appear, launching, shelf;
    private final Field dependency, header;
    private final Method dependencyGet, mustStay;
    private final Class<?> headerClass, expandable;
    private View headerView;
    private Object owner, stack;
    private Canvas canvas;
    private Shader original, continuous;
    private int saveCount, cacheHeight = -1, cacheStart, cacheEnd, cacheBottom;

    NotificationEdgeFade(Class<?> stackClass, Class<?> expandableClass, ClassLoader cl)
            throws ReflectiveOperationException {
        expandable = expandableClass;
        mustStay = expandableClass.getMethod("mustStayOnScreen");
        mustStay.setAccessible(true);
        ambient = stackClass.getField("mAmbientState");
        scrim = ambient.getType().getField("mNotificationScrimTop");
        appear = stackClass.getField("mLastSentAppear");
        launching = stackClass.getDeclaredField("mLaunchingNotification");
        shelf = stackClass.getField("mShelf");
        Field nativeDrawing;
        try { nativeDrawing = stackClass.getField("mDrawingEdgeFadeOut"); }
        catch (NoSuchFieldException legacy) { nativeDrawing = null; }
        drawing = nativeDrawing;
        if (drawing != null) {
            shader = stackClass.getField("mFadeClipShaderForDraw");
            topStart = stackClass.getField("mFadeClipTopStart");
            topEnd = stackClass.getField("mFadeTopEnd");
            bottomStart = stackClass.getField("mFadeBottomStart");
            dependency = header = null;
            dependencyGet = null;
            headerClass = null;
        } else {
            shader = topStart = topEnd = bottomStart = null;
            Class<?> dependencies = XposedHelpers.findClass("com.android.systemui.Dependency", cl);
            dependency = dependencies.getField("sDependency");
            Method get;
            try { get = dependencies.getMethod("getDependencyInner", Class.class); }
            catch (NoSuchMethodException legacyKey) { get = dependencies.getMethod("getDependencyInner", Object.class); }
            dependencyGet = get;
            headerClass = XposedHelpers.findClass("com.android.systemui.shade.ShadeHeaderController", cl);
            header = headerClass.getField("header");
        }
        for (Field field : new Field[] {drawing,shader,topStart,topEnd,bottomStart,ambient,scrim,
                appear,launching,shelf,dependency,header}) if (field != null) field.setAccessible(true);
    }

    void begin(Object token, Object host, Canvas target, View child) throws ReflectiveOperationException {
        if (owner != null || child == shelf.get(host) || launching.getBoolean(host)
                || appear.getFloat(host) <= 0f || !expandable.isInstance(child)) return;
        if (drawing != null) {
            if (!drawing.getBoolean(host)) return;
            int end = topEnd.getInt(host), bottom = bottomStart.getInt(host);
            float childY = child.getY();
            // Match native drawChild exactly: central children never consume the shader.
            // Avoid a cache build and the temporary field swap/restore for those draws.
            if (!(childY < end || child.getHeight() + childY > bottom)) return;
            Shader nativeShader = (Shader) shader.get(host);
            if (nativeShader == null) return;
            if ((Boolean) mustStay.invoke(child, NO_ARGS)) return;
            int height = ((View) host).getHeight(), start = topStart.getInt(host);
            if (continuous == null || height != cacheHeight || start != cacheStart
                    || end != cacheEnd || bottom != cacheBottom) {
                EdgeFadeMask mask = new EdgeFadeMask(height,start,end,bottom);
                continuous = new LinearGradient(0f,0f,0f,Math.max(1,height),mask.colors,
                        mask.positions,Shader.TileMode.CLAMP);
                cacheHeight=height;cacheStart=start;cacheEnd=end;cacheBottom=bottom;
            }
            original = nativeShader;
            stack = host;
            owner = token;
            shader.set(host, continuous);
        } else {
            if ((Boolean) mustStay.invoke(child, NO_ARGS)) return;
            if (headerView == null || !headerView.isAttachedToWindow())
                headerView = (View) header.get(dependencyGet.invoke(dependency.get(null), headerClass));
            if (headerView == null) return;
            float start = Math.min(headerView.getMeasuredHeight(),(int)scrim.getFloat(ambient.get(host))) - 20f;
            canvas = target;
            saveCount = canvas.save();
            owner = token;
            // Legacy SystemUI applies DST_IN only inside the fade strip. Hide the region above
            // its zero-alpha edge, which otherwise preserves an opaque, abruptly cut card.
            canvas.clipRect(-1_000_000f,start,1_000_000f,1_000_000f);
        }
    }

    void end(Object token) throws IllegalAccessException {
        if (owner != token) return;
        try {
            if (drawing != null) shader.set(stack, original);
            else canvas.restoreToCount(saveCount);
        } finally { owner=stack=null;canvas=null;original=null; }
    }
}
