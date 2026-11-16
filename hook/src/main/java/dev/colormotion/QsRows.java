package dev.colormotion;

import android.view.Choreographer;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Binds {@link QsModel} to One UI 8.5's Compose control center.
 *
 * Each grid item (EditableItem) and the header (SecQuickSettingHeader) get an extra graphicsLayer
 * whose block reads the model. Each motion row has a separate snapshot state, changed only when
 * its visible pose changes. Settled rows do not invalidate while other rows or blur continue
 * animating. Grid cell rows map to ColorOS nodes; the offset index restarts
 * below the block of tall items that starts at row 0 (ColorOS's personal component).
 */
final class QsRows {
    private static final int MAX_ROWS = QsModel.MAX_ROWS;
    private static final int HEADER = MAX_ROWS, FOOTER = MAX_ROWS + 1;

    interface FrameListener {
        void onQsFrame(QsModel model);
        default void onQsFrameTime(long frameTimeNanos) {}
        default void onQsFailure(Throwable failure) {}
    }

    final QsModel model = new QsModel();
    final QsClipping clipping;
    private final ClassLoader cl;
    private final HookSet hooks;
    private FrameListener frameListener;

    /** False = layers are identity (lock screen, other surfaces, or not driven by ShadeMotion). */
    private boolean armed;
    private boolean running;
    private MotionFrame motionFrame;
    private boolean installed;
    private boolean awaitingContent;
    private java.lang.ref.WeakReference<View> motionHost = new java.lang.ref.WeakReference<>(null);
    private final FrameClock frameClock = new FrameClock();
    private View contentHost;
    private ViewTreeObserver contentObserver;
    private ViewTreeObserver.OnPreDrawListener contentReady;
    private long contentGeneration;
    private View waitingHost;
    private final View.OnAttachStateChangeListener contentAttachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View host) {
            try {
                if (host != waitingHost) return;
                if (!armed || !awaitingContent) { stopObservingContent(); return; }
                // A sleeping wait has no vsync that could discover a replacement window tree.
                observeContent();
            } catch (Throwable failure) { contentReadinessFailed(failure); }
        }
        @Override public void onViewDetachedFromWindow(View host) {
            try {
                if (host == waitingHost) stopObservingPreDraw();
            } catch (Throwable failure) { contentReadinessFailed(failure); }
        }
    };
    private int maxRowSeen;
    private int topBlockEnd = 3;
    private boolean layoutDirty = true;

    private final LayerSignal[] signals = new LayerSignal[MAX_ROWS + 2];
    private Class<?> stateKt;
    private Method stateGet;
    private Method stateSet;
    private Method graphicsLayer;
    private Method modifierThen;
    private Method modifierFoldIn;
    private Object motionModifierFold;
    private Object modifierCompanion;
    private Class<?> function1;
    private final Object[] rowBlocks = new Object[MAX_ROWS];
    private Object headerBlock;
    private final Object[] rowModifiers = new Object[MAX_ROWS];
    private Object headerModifier;
    private Object footerModifier;
    private NativeLayers nativeLayers;
    private final Runnable[] pending = new Runnable[MAX_ROWS + 2];
    private final int[] pendingCount = new int[1];
    private Runnable commitLayers;
    private static final int MAX_PUBLICATION_FAILURES = 4;
    private PublicationFrame publicationFrame;
    private boolean publishing, publicationRequested, publicationFailed;
    private int publicationFailures;
    private Field layerAlpha, layerScaleX, layerScaleY, layerTranslationY, layerMutated, layerStrategy;

    QsRows(ClassLoader cl) {
        this(cl, new HookSet());
    }

    QsRows(ClassLoader cl, HookSet hooks) {
        this.cl = cl;
        this.hooks = hooks;
        this.clipping = new QsClipping(cl, hooks);
    }

    void setFrameListener(FrameListener l) {
        frameListener = l;
    }

    NativeLayers nativeBindings() { return nativeLayers; }

    void install() throws Throwable {
        install(true);
    }

    void install(boolean installViewportHook) throws Throwable {
        QsItemBinding itemBinding = new QsItemBinding(cl);
        if (installViewportHook) clipping.install();
        Class<?> composeModifier = XposedHelpers.findClass("androidx.compose.ui.Modifier", cl);
        function1 = XposedHelpers.findClass("kotlin.jvm.functions.Function1", cl);
        graphicsLayer = XposedHelpers.findMethodExact("androidx.compose.ui.graphics.GraphicsLayerModifierKt", cl,
                "graphicsLayer", composeModifier, function1);
        modifierThen = composeModifier.getMethod("then", composeModifier);
        Class<?> function2 = XposedHelpers.findClass("kotlin.jvm.functions.Function2", cl);
        modifierFoldIn = composeModifier.getMethod("foldIn", Object.class, function2);
        modifierCompanion = XposedHelpers.getStaticObjectField(composeModifier, "Companion");
        motionModifierFold = Proxy.newProxyInstance(cl, new Class<?>[] {function2}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "invoke":
                    int row = motionModifierIndex(args[1]);
                    if (args[0] instanceof Integer) {
                        int mask = (Integer) args[0];
                        if (row < 0) return mask;
                        int bit = 1 << row;
                        return mask | bit | ((mask & bit) == 0 ? 0 : 1 << 30);
                    }
                    return row < 0 ? modifierThen.invoke(args[0], args[1]) : args[0];
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                default: return "ColorMotionModifierFold";
            }
        });
        stateKt = XposedHelpers.findClass("androidx.compose.runtime.SnapshotStateKt", cl);
        stateGet = XposedHelpers.findClass("androidx.compose.runtime.State", cl).getMethod("getValue");
        stateSet = XposedHelpers.findClass("androidx.compose.runtime.MutableState", cl).getMethod("setValue", Object.class);
        Class<?> scope = XposedHelpers.findClass("androidx.compose.ui.graphics.ReusableGraphicsLayerScope", cl);
        layerAlpha = scope.getField("alpha");
        layerScaleX = scope.getField("scaleX");
        layerScaleY = scope.getField("scaleY");
        layerTranslationY = scope.getField("translationY");
        layerMutated = scope.getField("mutatedFields");
        layerStrategy = scope.getField("compositingStrategy");
        try {
            Object probe = XposedHelpers.callStaticMethod(stateKt, "mutableStateOf$default", Boolean.FALSE);
            nativeLayers = NativeLayers.load(cl, probe);
            commitLayers = nativeLayers.batch(pending, pendingCount);
            XposedBridge.log(Entry.TAG + ": typed QS layer bridge active");
        } catch (Throwable t) {
            nativeLayers = null;
            XposedBridge.log(Entry.TAG + ": native layer bridge unavailable, using reflective binding: " + t);
        }

        // EditableItem(Modifier cellData, String id, long topLeft(IntOffset x<<32|y), long size(w<<32|h), ...)
        // Kotlin mangles inline-class signatures with '-': jadx shows this as m3162EditableItemlVIIpV8.
        hooks.method(itemBinding.editableItem, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.args.length < 4 || !(param.args[2] instanceof Long)
                                    || !(param.args[3] instanceof Long) || param.args[0] == null) {
                                return;
                            }
                            if (!itemBinding.isMainGrid(param.args[0], (Long) param.args[2], (Long) param.args[3])) return;
                            int row = (int) ((Long) param.args[2] & 0xFFFFFFFFL);
                            int spanH = (int) ((Long) param.args[3] & 0xFFFFFFFFL);
                            if (row < 0 || row >= MAX_ROWS) {
                                return;
                            }
                            noteItem(row, spanH);
                            param.args[0] = withMotionModifier(param.args[0], rowModifier(row), row);
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });

        // One UI 9 removed the default-mask argument from this composable.
        hooks.method(itemBinding.header, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object base = itemBinding.headerBase(param.args, modifierCompanion);
                    itemBinding.replaceHeader(param.args, withMotionModifier(base, headerModifier(), HEADER));
                } catch (Throwable t) { log(t); }
            }
        });
        QsAuxBinding auxiliary = new QsAuxBinding(cl);
        hooks.method(auxiliary.footer, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object base = param.args[0] == null ? modifierCompanion : param.args[0];
                    param.args[0] = withMotionModifier(base, footerModifier(), FOOTER);
                } catch (Throwable t) { log(t); }
            }
        });
        hooks.method(auxiliary.banner, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object base = param.args[1] == null ? modifierCompanion : param.args[1];
                    // The banner precedes the grid, alongside the Compose clock. Apply the
                    // same header chain displacement so a heavy pull keeps their spacing.
                    param.args[1] = withMotionModifier(base, headerModifier(), HEADER);
                } catch (Throwable t) { log(t); }
            }
        });
        XposedBridge.log(Entry.TAG + ": compose hooks: EditableItem=1 [" + itemBinding.editableItem.getName() + "]"
                + " SecQuickSettingHeader=1 [args=" + itemBinding.header.getParameterCount() + "]");
        QsComposeLifecycle.installOptional(cl, hooks);
        installed = true;
    }

    /** Grid layout bookkeeping: tall items starting at row 0 form the top block. */
    private void noteItem(int row, int spanH) {
        if (row > maxRowSeen) {
            maxRowSeen = row;
            layoutDirty = true;
        }
        if (row == 0 && spanH > 1 && spanH > topBlockEnd) {
            topBlockEnd = spanH;
            layoutDirty = true;
        }
        if (layoutDirty && armed) start();
    }

    private void applyLayout() {
        if (!layoutDirty) {
            return;
        }
        layoutDirty = false;
        for (int r = 0; r < MAX_ROWS; r++) {
            model.nodeOfRow[r] = r;
            model.subIndexOfRow[r] = r < topBlockEnd ? r : r - topBlockEnd;
        }
        model.setRowCount(maxRowSeen + 1);
    }

    // ---------------------------------------------------------------- driving

    void setArmed(boolean armed) {
        clipping.setActive(armed);
        if (!armed) {
            // An inactive surface must not keep advancing springs, moving its native host or
            // submitting blur while the other shade owns the gesture (or the phone is locked).
            stopMotion();
            awaitingContent = false;
            stopObservingContent();
        }
        if (this.armed != armed) {
            this.armed = armed;
            if (armed && awaitingContent) observeContent();
            publishLayers();
        }
    }

    /** Applies layout bookkeeping before a gesture starts. */
    void prepare() {
        applyLayout();
    }

    boolean isRunning() { return running; }

    void setMotionHost(View host) {
        if (motionHost.get() != host) {
            stopObservingContent();
            motionHost = new java.lang.ref.WeakReference<>(host);
        }
        if (armed && awaitingContent) observeContent();
    }

    private void observeContent() {
        View host = motionHost.get();
        if (host == null) return;
        if (waitingHost != host) {
            stopObservingContent();
            waitingHost = host;
            host.addOnAttachStateChangeListener(contentAttachment);
        }
        ViewTreeObserver observer = host.getViewTreeObserver();
        if (contentHost == host && contentObserver == observer && observer.isAlive()) return;
        stopObservingPreDraw();
        if (!observer.isAlive()) return;
        contentHost = host;
        contentObserver = observer;
        long generation = contentGeneration;
        contentReady = () -> onContentPreDraw(generation);
        observer.addOnPreDrawListener(contentReady);
    }

    private void stopObservingPreDraw() {
        // Removal does not cancel an old tree's already captured dispatch snapshot.
        // Its listener must not satisfy a newer tree's readiness wait.
        contentGeneration++;
        if (contentObserver != null && contentObserver.isAlive())
            contentObserver.removeOnPreDrawListener(contentReady);
        // Android can merge a floating observer into a different live tree and kill the
        // stored object. Remove the migrated listener before losing its host reference.
        if (contentHost != null) {
            ViewTreeObserver current = contentHost.getViewTreeObserver();
            if (current != contentObserver && current.isAlive())
                current.removeOnPreDrawListener(contentReady);
        }
        contentObserver = null;
        contentHost = null;
        contentReady = null;
    }

    private void stopObservingContent() {
        stopObservingPreDraw();
        if (waitingHost != null) waitingHost.removeOnAttachStateChangeListener(contentAttachment);
        waitingHost = null;
    }

    private void contentReadinessFailed(Throwable failure) {
        awaitingContent = false;
        stopObservingContent();
        stopMotion();
        log(failure);
        if (frameListener != null) frameListener.onQsFailure(failure);
    }

    private boolean onContentPreDraw(long generation) {
        try {
            if (generation != contentGeneration) return true;
            if (!armed || !awaitingContent) { stopObservingContent(); return true; }
            View host = contentHost;
            if (host == null || host != motionHost.get() || !host.isAttachedToWindow()
                    || !host.isShown() || host.isLayoutRequested() || host.getWidth() <= 0
                    || host.getHeight() <= 0 || headerBlock == null || rowBlocks[0] == null) return true;
            // Cached blocks and visibility are not proof that a reattached scene completed layout.
            // Start the reveal after that traversal, excluding its cold work from entrance time.
            awaitingContent = false;
            stopObservingContent();
            model.setOpeningContentReady(true);
            frameClock.reset();
            // A hidden/detached host can outlive every active spring. Its pre-draw observer,
            // rather than an otherwise idle vsync loop, wakes the reveal after layout.
            start();
        } catch (Throwable failure) {
            contentReadinessFailed(failure);
        }
        return true;
    }

    void beginOpening(boolean closed) {
        if (!closed) return;
        // A cached scene still needs its native host to become visible on each fresh opening.
        stopObservingContent();
        awaitingContent = installed;
        model.setOpeningContentReady(!awaitingContent);
        if (armed && awaitingContent) observeContent();
        frameClock.reset();
        if (armed) publishLayers();
    }

    void start() {
        if (armed && !running && !publicationFailed) {
            MotionFrame frame = new MotionFrame();
            motionFrame = frame;
            running = true;
            frameClock.reset();
            // Publish the reset/hidden pose before the first traversal; the clock's first
            // callback only establishes its timestamp and must not expose stale opaque rows.
            publishLayers();
            if (isCurrent(frame)) frame.post();
        }
    }

    void snapHidden() {
        // A fully collapsed QS may remain armed until the native state/side changes later.
        // Its discovery/lifecycle scope must still sleep; the next setArmed(true) reopens it.
        clipping.setActive(false);
        applyLayout();
        model.reset();
        awaitingContent = false;
        stopObservingContent();
        stopMotion();
        if (frameListener != null) {
            frameListener.onQsFrame(model);
        }
        publishLayers();
    }

    private void stopMotion() {
        MotionFrame frame = motionFrame;
        motionFrame = null;
        running = false;
        frameClock.reset();
        if (frame != null) Choreographer.getInstance().removeFrameCallback(frame);
    }

    private boolean isCurrent(MotionFrame frame) {
        return motionFrame == frame && running && armed;
    }

    /** A removed callback may already be in Choreographer's dispatch snapshot. */
    private final class MotionFrame implements Choreographer.FrameCallback {
        boolean queued;
        void post() {
            if (!queued && isCurrent(this)) {
                queued = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
        @Override public void doFrame(long nanos) {
            if (!isCurrent(this)) return;
            queued = false;
            onMotionFrame(this, nanos);
        }
    }

    private void onMotionFrame(MotionFrame frame, long frameTimeNanos) {
        android.os.Trace.beginSection("ColorMotion.QSFrame");
        try { advanceFrame(frame, frameTimeNanos); }
        catch (Throwable failure) {
            stopMotion();
            awaitingContent = false;
            stopObservingContent();
            log(failure);
            if (frameListener != null) frameListener.onQsFailure(failure);
        }
        finally { android.os.Trace.endSection(); }
    }

    private void advanceFrame(MotionFrame frame, long frameTimeNanos) {
        if (!isCurrent(frame)) return;
        if (frameListener != null) frameListener.onQsFrameTime(frameTimeNanos);
        if (!isCurrent(frame)) return;
        applyLayout();
        if (awaitingContent) observeContent();
        if (!frameClock.isStarted()) {
            frameClock.start(frameTimeNanos);
            frame.post();
            return;
        }
        float durationScale = android.animation.ValueAnimator.getDurationScale();
        long step = frameClock.advance(frameTimeNanos, durationScale);
        if (durationScale == 0f) model.finishRebound();
        boolean busy = model.step(step);
        if (frameListener != null) {
            frameListener.onQsFrame(model);
        }
        if (!isCurrent(frame)) return;
        publishLayers();
        if (!isCurrent(frame)) return;
        if (busy) frame.post();
        else stopMotion();
    }

    // ---------------------------------------------------------------- Compose plumbing

    /** Native restart lambdas capture the decorated argument and call the same hook again. */
    private Object withMotionModifier(Object base, Object motion, int row) throws ReflectiveOperationException {
        if (base == motion) return base;
        int mask = (Integer) modifierFoldIn.invoke(base, 0, motionModifierFold);
        if (mask == (1 << row)) return base;
        // Preserve all native elements and their order. A moved grid cell must replace its old
        // row layer, rather than nesting two different motion transforms. Also repair duplicate
        // owned elements if a captured modifier is supplied more than once.
        if (mask != 0) base = modifierFoldIn.invoke(base, modifierCompanion, motionModifierFold);
        return modifierThen.invoke(base, motion);
    }

    private int motionModifierIndex(Object element) {
        if (element == null) return -1;
        if (element == headerModifier) return HEADER;
        if (element == footerModifier) return FOOTER;
        for (int row = 0; row < MAX_ROWS; row++) if (element == rowModifiers[row]) return row;
        return -1;
    }

    /** Publish changed poses together, synchronously, before this frame's traversal. */
    private void publishLayers() {
        // Snapshot apply observers and reflected state writers may reenter driving methods.
        // Keep their newer pose/queue changes out of the in-flight transaction, then publish
        // that latest pose on the next callback. Never recursively commit the same batch.
        if (publishing) { publicationRequested = true; return; }
        cancelPublicationFrame();
        publishing = true;
        publicationRequested = false;
        Throwable failure = null;
        int count = 0;
        try {
            for (int row = 0; row < signals.length; row++) {
                LayerSignal signal = signals[row];
                if (signal == null || !updatePose(row, signal) || signal.queued) continue;
                signal.queued = true;
                pending[pendingCount[0]++] = signal.publish;
            }
            count = pendingCount[0];
            if (count != 0) {
                if (commitLayers != null) commitLayers.run();
                else {
                    for (int i = 0; i < count; i++) pending[i].run();
                    pendingCount[0] = 0;
                }
                for (LayerSignal signal : signals) if (signal != null) signal.queued = false;
                java.util.Arrays.fill(pending, 0, count, null);
            }
            publicationFailures = 0;
        } catch (Throwable t) {
            // Keep dirty entries queued. NativeBatch restores/disposes its snapshot on failure;
            // it derives the next signal from the actual state, so a retry cannot lose a toggle.
            // A backend may throw during post-apply cleanup, after clearing its count. Keep
            // the entries retryable even then; a new toggle is derived from committed state.
            if (pendingCount[0] == 0) pendingCount[0] = count;
            log(t);
            failure = t;
        } finally {
            publishing = false;
        }
        if (failure != null && !publicationFailed && ++publicationFailures >= MAX_PUBLICATION_FAILURES) {
            // Latch before notifying: failOpen disarms and attempts identity publication itself.
            // A permanently broken backend must not recurse into recovery or keep a vsync loop.
            publicationFailed = true;
            stopMotion();
            awaitingContent = false;
            stopObservingContent();
            try { if (frameListener != null) frameListener.onQsFailure(failure); }
            catch (Throwable recoveryFailure) { log(recoveryFailure); }
        }
        if (!publicationFailed && (pendingCount[0] != 0 || publicationRequested)) {
            publicationFrame = new PublicationFrame();
            Choreographer.getInstance().postFrameCallback(publicationFrame);
        }
    }

    private void cancelPublicationFrame() {
        PublicationFrame frame = publicationFrame;
        publicationFrame = null;
        if (frame != null) Choreographer.getInstance().removeFrameCallback(frame);
    }

    /** Publication retries outlive model settling/disarm, but never run native motion or blur. */
    private final class PublicationFrame implements Choreographer.FrameCallback {
        @Override public void doFrame(long nanos) {
            if (publicationFrame != this) return;
            publicationFrame = null;
            publishLayers();
        }
    }

    private boolean updatePose(int row, LayerSignal signal) {
        boolean header = row == HEADER, footer = row == FOOTER;
        boolean active = armed && (header || footer || row < model.rowCount);
        float alpha = active ? (header ? model.headerOpacity() : footer ? model.footerOpacity() : model.rowAlpha(row)) : 1f;
        float scale = active ? (header ? model.headerScale.value : footer ? model.footerScale() : model.scale[row].value) : 1f;
        // Native host translation is already applied to all Compose content once.
        float y = active ? (header ? model.headerTranslation() : footer
                ? model.footerTranslation() - model.translation.value : model.rowTranslation(row)) : 0f;
        if (!signal.pose.update(alpha, scale, y)) return false;
        signal.values[0] = alpha;
        signal.values[1] = scale;
        signal.values[2] = y;
        return true;
    }

    private final class LayerSignal {
        final Object state = XposedHelpers.callStaticMethod(stateKt, "mutableStateOf$default", Boolean.FALSE);
        final MotionPose pose = new MotionPose();
        final float[] values = {1f, 1f, 0f};
        Runnable publish;
        boolean queued;
    }

    private Object rowBlock(int row) {
        Object block = rowBlocks[row];
        if (block == null) rowBlocks[row] = block = layerBlock(row);
        return block;
    }

    private Object rowModifier(int row) throws ReflectiveOperationException {
        Object modifier = rowModifiers[row];
        if (modifier == null) rowModifiers[row] = modifier =
                graphicsLayer.invoke(null, modifierCompanion, rowBlock(row));
        return modifier;
    }

    private Object headerModifier() throws ReflectiveOperationException {
        if (headerModifier == null) headerModifier =
                graphicsLayer.invoke(null, modifierCompanion, headerBlock());
        return headerModifier;
    }

    private Object headerBlock() {
        if (headerBlock == null) headerBlock = layerBlock(HEADER);
        return headerBlock;
    }

    private Object footerModifier() throws ReflectiveOperationException {
        if (footerModifier == null) footerModifier =
                graphicsLayer.invoke(null, modifierCompanion, layerBlock(FOOTER));
        return footerModifier;
    }

    /** Same scope fields/dirty bits as its setters, without boxed floats and varargs per layer. */
    private void applyLayer(Object scope, float alpha, float scale, float y) throws IllegalAccessException {
        layerAlpha.setFloat(scope, QsModel.clamp(alpha, 0f, 1f));
        layerScaleX.setFloat(scope, scale);
        layerScaleY.setFloat(scope, scale);
        layerTranslationY.setFloat(scope, y);
        // This wrapper must not create a bounded alpha texture around moving child layers.
        // Individual rounded masks and explicit offscreen/effect layers retain native compositing.
        layerStrategy.setInt(scope, 2); // Samsung Compose CompositingStrategy.ModulateAlpha
        // NodeCoordinator resets the reusable scope before invoking a block. Native setters
        // mark only departures from those defaults; OwnedLayer also merges the previous
        // mask to restore properties that have returned to identity. Matching that contract
        // avoids redundant scale/alpha/translation work during other properties' tails.
        int changed = 32768;
        if (alpha != 1f) changed |= 4;
        if (scale != 1f) changed |= 1 | 2;
        if (y != 0f) changed |= 16;
        layerMutated.setInt(scope, layerMutated.getInt(scope) | changed);
    }

    private Object layerBlock(int row) {
        applyLayout();
        if (row < MAX_ROWS) model.noteRowReady(row);
        LayerSignal signal = new LayerSignal();
        signals[row] = signal;
        updatePose(row, signal);
        if (nativeLayers != null) {
            try {
                Object block = nativeLayers.layer(signal.state, signal.values);
                signal.publish = (Runnable) block;
                return block;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        // Compatibility path for builds whose native ABI differs. The bridge is probed before
        // any layer is created, so one panel cannot mix half-initialized binding strategies.
        Object unit = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", cl), "INSTANCE");
        signal.publish = () -> {
            try {
                Object value = stateGet.invoke(signal.state);
                stateSet.invoke(signal.state, value == Boolean.TRUE ? Boolean.FALSE : Boolean.TRUE);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        };
        InvocationHandler h = (proxy, method, args) -> {
            switch (method.getName()) {
                case "invoke":
                    stateGet.invoke(signal.state);
                    applyLayer(args[0], signal.values[0], signal.values[1], signal.values[2]);
                    return unit;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "ColorMotionLayer";
                default: return null;
            }
        };
        return Proxy.newProxyInstance(cl, new Class<?>[] {function1}, h);
    }

    private static void log(Throwable t) {
        XposedBridge.log(Entry.TAG + ": " + t);
    }
}
