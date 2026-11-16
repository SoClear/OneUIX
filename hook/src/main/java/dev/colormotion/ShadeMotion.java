package dev.colormotion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.VelocityTracker;
import android.view.View;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Layered panel motion for One UI 8.5's split shade.
 *
 * Samsung drives every shade visual from NotificationPanelViewController's expanded height, so the
 * ColorOS fraction spring is inserted between the finger and setExpandedHeightInternal(), and the
 * release fling is replaced by the same spring. The reference-fitted QS flow drives both the
 * Compose control center and notification cards, preserving Samsung's notification layout.
 */
final class ShadeMotion {
    private static final String NPVC = "com.android.systemui.shade.NotificationPanelViewController";
    private static final String SPLIT_OPEN_ANIMATOR = "com.android.systemui.qs.animator.PanelSplitOpenAnimator";

    private static final int SIDE_QS = 0;
    private static final int SIDE_SHADE = 1;

    private final ClassLoader cl;
    private final HookSet hooks = new HookSet();

    private Object npvc;
    private PanelFields panel;
    private boolean injecting;
    private boolean failed;
    private boolean inFling;
    private int side = SIDE_SHADE;
    private boolean dragging;
    // A physical gesture outlives tracking changes and repeated native fling callbacks.
    private boolean gestureInitialized;
    private boolean gestureReleased;
    private boolean openingReveal;
    private long gestureDownTime = Long.MIN_VALUE;
    private int dragDispatchDepth;
    private boolean panelClosedOnDown;
    private float trackingStartVelocity;
    private float dragStartFraction;
    private boolean panelCollapsed = true;
    private boolean heightDriven;
    private boolean panelSwitching;
    private PanelSlideMotion panelSlide;
    private int heightDispatchDepth;
    private float lastHeightFraction;
    /** A primed opening drag must not return to height 0, which hides the shade window. */
    private boolean openingPrimed;
    /** DOWN of a QS pull from a collapsed panel, until its UP/CANCEL. */
    private boolean qsTouchDown;

    /** PanelExpandedFraction / QSPanelExpandFraction.fractionAnimation while the finger is down. */
    private final SpringDriver fraction = new SpringDriver(new SpringDriver.Listener() {
        @Override
        public void onUpdate(Spring s) {
            try { applyFraction(s.value); }
            catch (Throwable failure) { failOpen(failure); }
        }
    });

    private FlingCurve activeCurve;
    private final FlingCurve.Workspace flingWorkspace = new FlingCurve.Workspace();

    // Notifications deliberately reuse the QS reference flow, with one card per row.
    private final NotificationRows notificationRows = new NotificationRows();
    private NotificationGeometry notificationGeometry;
    private NotificationFooter notificationFooter;
    private boolean notificationListNative;
    private boolean notificationScrollKnown;
    private int notificationScrollAtStart;
    private boolean notificationDriven;
    private boolean notificationAnimating;
    private final OpeningReadiness notificationOpening = new OpeningReadiness(new OpeningReadiness.Listener() {
        @Override public boolean isReady(View host) throws IllegalAccessException {
            return ownsNotificationGeometry(host) && !notificationRows.layoutPending(host);
        }
        @Override public void onReady() {
            notificationRows.model.setOpeningContentReady(true);
            // Exclude the first native layout's work from the entrance clock, as QS does.
            notifClock.reset();
            queueNotifFrame();
        }
        @Override public void onFailure(Throwable failure) { failOpen(failure); }
    });
    private XC_MethodHook.MethodHookParam notificationStateOwner;
    private java.util.List<NotificationRows.State> notificationApplied;
    /** Samsung's SecQpBlurController, captured from its first getCalculatedFraction() call. */
    private Object blurController;
    private Object splitOpenAnimator;
    private SplitViewBinding splitViewBinding;
    private final View[] splitHeaderViews = new View[3];
    private final View[] splitBlurViews = new View[3];
    private final boolean[] nestedHeader = new boolean[3];
    private WeakReference<View> qsMotionView = new WeakReference<>(null);
    private WeakReference<View> notificationStack = new WeakReference<>(null);
    private WeakReference<Object> notificationStackPanel = new WeakReference<>(null);
    private float lastRequestedBlur = Float.NaN;
    private final BlurFrameGate blurFrameGate = new BlurFrameGate();
    private BlurFrameBinding blurFrame;
    private Field mirrorVisible, bouncerShowing;
    private QsSceneBridge qsScene;
    /** True while ColorOS blur springs own the blur (from a hooked gesture until fully collapsed). */
    private boolean blurDriven;

    /** One clock advances the notification header, card cascade, blur and rebound. */
    private boolean notifFrameQueued;
    private final FrameClock notifClock = new FrameClock();
    private NotificationRun notifRun;
    private final class NotificationRun implements android.view.Choreographer.FrameCallback {
        @Override public void doFrame(long frameTimeNanos) {
            // Removed callbacks may still be in the current Choreographer dispatch snapshot.
            if (notifRun == this) notifFrame.doFrame(frameTimeNanos);
        }
    }
    private final android.view.Choreographer.FrameCallback notifFrame = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            android.os.Trace.beginSection("ColorMotion.NotificationFrame");
            try { advance(frameTimeNanos); }
            catch (Throwable failure) { failOpen(failure); }
            finally { android.os.Trace.endSection(); }
        }

        private void advance(long frameTimeNanos) {
            blurFrameGate.beginFrame(frameTimeNanos);
            notifFrameQueued = false;
            if (panelSwitching) return;
            if (!notificationDriven || side != SIDE_SHADE || npvc == null
                    || panel.barState(npvc) != 0) {
                stopNotificationMotion();
                return;
            }
            View stack = nssl();
            notificationOpening.setHost(stack);
            boolean rowsPrepared = false;
            if (stack != null) {
                try {
                    notificationRows.prepare(stack);
                    rowsPrepared = true;
                }
                catch (IllegalAccessException e) { log(e); }
                noticeNotificationScroll(stack);
            }
            float scale = ValueAnimator.getDurationScale();
            if (scale == 0f) notificationRows.model.finishRebound();
            boolean busy = notificationRows.model.step(notifClock.advance(frameTimeNanos, scale));
            notificationAnimating = busy;
            if (stack != null) {
                android.os.Trace.beginSection("ColorMotion.NotificationRender");
                try {
                    if (!notificationListNative) {
                        renderingCards = true;
                        try {
                            if (rowsPrepared) notificationRows.renderPrepared(stack);
                            else notificationRows.render(stack);
                        } finally { renderingCards = false; }
                    }
                    if (notificationFooter != null) notificationFooter.render(stack, notificationRows.model, false);
                }
                catch (ReflectiveOperationException e) {
                    // A native gradient/property call can fail after visibility is committed.
                    // Logging alone strands that pose with no retry once the clock settles.
                    // Use the same full native repair as the typed path's runtime failure.
                    throw new IllegalStateException("Notification render failed", e);
                }
                finally { android.os.Trace.endSection(); }
            }
            if (splitOpenAnimator != null) applySplitHeaderPosition(splitOpenAnimator);
            requestBlurFrame();
            if (traversalOrder != null) traversalOrder.drawLast(stack);
            if (busy) queueNotifFrame();
            else if (!notifFrameQueued) {
                notifClock.reset();
                notifRun = null;
            }
        }
    };

    // Control center side: QsModel (QSPanelExpandFraction/QSPanelTranslationY/elements/chain)
    private final QsRows qsRows;
    private final TraversalOrder traversalOrder = TraversalOrder.create();

    ShadeMotion(ClassLoader cl) {
        this.cl = cl;
        this.qsRows = new QsRows(cl, hooks);
        qsRows.setFrameListener(new QsRows.FrameListener() {
            @Override public void onQsFrameTime(long frameTimeNanos) { blurFrameGate.beginFrame(frameTimeNanos); }
            @Override public void onQsFrame(QsModel m) {
                applyQsTranslation(m.translation.value);
            // The header must keep following the model after the native height animator ends.
            if (splitOpenAnimator != null) applySplitHeaderPosition(splitOpenAnimator);
            requestBlurFrame();
            if (traversalOrder != null) traversalOrder.drawLast(qsMotionView.get());
            }
            @Override public void onQsFailure(Throwable failure) { failOpen(failure); }
        });
    }

    /** A late native ABI failure must not escape a Choreographer callback and crash SystemUI. */
    private void failOpen(Throwable failure) {
        if (failed) return;
        failed = true;
        hooks.rollback();
        fraction.cancel();
        blurDriven = dragging = inFling = false;
        activeCurve = null;
        panelSwitching = false;
        try { if (panelSlide != null) panelSlide.abort(); } catch (Throwable restore) { log(restore); }
        cancelNotifFrames();
        try { qsRows.setArmed(false); } catch (Throwable restore) { log(restore); }
        try { stopNotificationMotion(); } catch (Throwable restore) { log(restore); }
        notificationDriven = notificationAnimating = false;
        try { requestNsslUpdate(); } catch (Throwable restore) { log(restore); }
        XposedBridge.log(Entry.TAG + ": motion disabled after runtime binding failure: " + failure);
    }

    void install() {
        try { installHooks(); }
        catch (Throwable failure) {
            hooks.rollback();
            qsRows.setArmed(false);
            throw new IllegalStateException("Motion bindings incompatible; all motion hooks removed", failure);
        }
    }

    private void installHooks() throws Throwable {
        qsRows.install(false);
        Class<?> npvcClass = XposedHelpers.findClass(NPVC, cl);
        try { panel = new PanelFields(npvcClass); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Panel binding failed", e); }

        installTouchHooks(XposedHelpers.findClass(NPVC + "$TouchHandler", cl));
        panelSlide = new PanelSlideMotion(cl);
        hooks.method(panelSlide.slide, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    panelSlide.abort();
                    if (canSwitchPanels() && panelSlide.allowed(param.thisObject) && panelSlide.isPanelTransition(param.args)
                            && panelSlide.state(param.thisObject) == 2 && !panelSwitching)
                        beginPanelSwitch();
                } catch (Throwable failure) { failOpen(failure); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (canSwitchPanels()) afterPanelSlide(param.thisObject, param.args);
                } catch (Throwable failure) { failOpen(failure); }
            }
        });

        hooks.findAndHookMethod(npvcClass, "setExpandedHeightInternal", float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                heightDispatchDepth++;
                android.os.Trace.beginSection("ColorMotion.PanelHeight");
                try {
                    beforeSetExpandedHeight(param);
                } catch (Throwable t) {
                    log(t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    afterSetExpandedHeight(param.thisObject);
                } catch (Throwable t) {
                    log(t);
                } finally {
                    heightDispatchDepth--;
                    android.os.Trace.endSection();
                }
            }
        });
        PanelHeightBinding height = new PanelHeightBinding(cl, npvcClass);
        hooks.method(height.run, createHeightConsumerHook(height));

        hooks.findAndHookMethod(npvcClass, "flingToHeight",
                float.class, boolean.class, float.class, float.class, boolean.class, createFlingHook());

        // SecQpBlurController.getCalculatedFraction() feeds both the panel blur (doBlur) and the panel
        // background alpha; ColorOS drives its blur, scrim alpha and wallpaper zoom linearly from the
        // blur fraction, so the fraction is replaced here.
        Class<?> blurClass = XposedHelpers.findClass("com.android.systemui.blur.SecQpBlurController", cl);
        blurFrame = new BlurFrameBinding(blurClass, cl);
        mirrorVisible = XposedHelpers.findField(blurClass, "isMirrorVisible");
        bouncerShowing = XposedHelpers.findField(blurClass, "isBouncerShowing");
        hooks.method(blurFrame.frame, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object controller = blurFrame.controller(param);
                    float owned = isMotionRunning() ? ownedBlurFraction(controller) : Float.NaN;
                    if (Float.isNaN(owned)) { blurFrameGate.reset(); return; }
                    if (!blurFrameGate.shouldApply(controller, owned)) param.setResult(blurFrame.skippedResult);
                } catch (Throwable t) { blurFrameGate.reset(); log(t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) blurFrameGate.reset();
            }
        });
        hooks.findAndHookMethod(blurClass, "getCalculatedFraction", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            blurController = param.thisObject;
                            float f = ownedBlurFraction(param.thisObject);
                            if (!Float.isNaN(f)) {
                                param.setResult(f);
                            }
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });

        // Replace Samsung's card expansion transforms at the final state consumers. Restore the
        // native state afterwards, so repeat applications never accumulate motion offsets.
        Class<?> nsslClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", cl);
        try {
            installNotificationGeometry(nsslClass);
            notificationRows.bind(nsslClass, XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableView", cl));
            notificationRows.bindRendering(XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", cl));
            installNotificationFooter(nsslClass, XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableView", cl));
            installBlurBackgroundCache();
            installNotificationEdgeFade(nsslClass, XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableView", cl));
            notificationRows.installNative(qsRows.nativeBindings());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Notification state binding failed", e);
        }
        XC_MethodHook applyCards = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (notificationStateOwner != null || !notificationDriven || param.thisObject != nssl()) return;
                boolean cards = ownsNotificationMotion();
                boolean footer = notificationFooter != null && ownsNotificationGeometry(param.thisObject);
                if (!cards && !footer) return;
                notificationStateOwner = param;
                android.os.Trace.beginSection("ColorMotion.StackState");
                try {
                    if (cards) {
                        int rows = notificationRows.model.rowCount;
                        notificationApplied = notificationRows.apply(param.thisObject);
                        if (rows != notificationRows.model.rowCount) queueNotifFrame();
                    }
                    if (footer) notificationFooter.apply(param.thisObject, notificationRows.model);
                } catch (Throwable t) {
                    log(t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (notificationStateOwner != param) return;
                try {
                    notificationRows.restoreApplied(notificationApplied);
                } catch (Throwable t) {
                    log(t);
                } finally {
                    if (notificationFooter != null) {
                        try {
                            notificationFooter.restore();
                            if (ownsNotificationGeometry(param.thisObject))
                                notificationFooter.render(param.thisObject, notificationRows.model, true);
                        } catch (Throwable t) { log(t); }
                    }
                    notificationApplied = null;
                    notificationStateOwner = null;
                    android.os.Trace.endSection();
                }
            }
        };
        hooks.findAndHookMethod(nsslClass, "applyCurrentState", applyCards);
        hooks.findAndHookMethod(nsslClass, "startAnimationToState$1", applyCards);
        XposedBridge.log(Entry.TAG + ": notification QS cascade state hooks installed");

        // Samsung's own split-open motion slides the header, clock and blur layers in from above
        // (translationY -50/-150 -> 0). ColorOS moves these with the panel translation instead.
        try { qsScene = new QsSceneBridge(cl); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("QS scene binding failed", e); }
        hooks.findAndHookMethod(XposedHelpers.findClass(SPLIT_OPEN_ANIMATOR, cl), "setPosition$1",
                float.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!blurDriven || npvc == null || panel.barState(npvc) != 0) return;
                        try {
                            if (qsScene.apply(param.thisObject, (Float) param.args[0])) param.setResult(null);
                        } catch (Throwable t) { log(t); }
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            afterSplitOpenPosition(param.thisObject);
                        } catch (Throwable t) {
                            log(t);
                        }
                    }
                });
        hooks.findAndHookMethod(XposedHelpers.findClass(SPLIT_OPEN_ANIMATOR, cl), "updateAnimators",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try { captureSplitViews(param.thisObject); }
                        catch (Throwable t) { log(t); }
                    }
                });
        // Vector can retain an empty trampoline after rollback. Bind the broad factory last.
        qsRows.clipping.install();
    }

    /** Set only around the module's render-only card pass; Samsung's own calls stay uncached. */
    private boolean renderingCards;
    private final java.util.IdentityHashMap<Object, Boolean> blurBackground = new java.util.IdentityHashMap<>();

    /**
     * Samsung's card setAlpha() re-derives isBlurBackground() on every call: color picker,
     * colorized/media checks, power-saving/theme/transparency settings and a resource lookup.
     * That was ~0.2 ms per fading card per frame. Its inputs cannot change within one opening,
     * so render-pass calls reuse each card's first result until the motion stops.
     */
    private void installBlurBackgroundCache() {
        try {
            Method method = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ActivatableNotificationView", cl)
                    .getDeclaredMethod("isBlurBackground");
            if (method.getReturnType() != boolean.class) throw new NoSuchMethodException("isBlurBackground ABI");
            hooks.method(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!renderingCards) return;
                    Boolean cached = blurBackground.get(param.thisObject);
                    if (cached != null) param.setResult(cached);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (renderingCards && !param.hasThrowable() && param.getResult() instanceof Boolean)
                        blurBackground.put(param.thisObject, (Boolean) param.getResult());
                }
            });
            XposedBridge.log(Entry.TAG + ": card blur-background cache installed");
        } catch (Throwable unavailable) {
            XposedBridge.log(Entry.TAG + ": card blur-background cache unavailable: " + unavailable);
        }
    }

    private void installNotificationEdgeFade(Class<?> stackClass, Class<?> expandableClass)
            throws ReflectiveOperationException {
        NotificationEdgeFade edge = new NotificationEdgeFade(stackClass, expandableClass, cl);
        hooks.findAndHookMethod(stackClass,"drawChild",android.graphics.Canvas.class,View.class,
                long.class,new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!ownsNotificationGeometry(param.thisObject)) return;
                try { edge.begin(param,param.thisObject,(android.graphics.Canvas)param.args[0],(View)param.args[1]); }
                catch (Throwable t) { log(t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try { edge.end(param); } catch (Throwable t) { log(t); }
            }
        });
        XposedBridge.log(Entry.TAG + ": continuous notification edge fade installed");
    }

    private void installNotificationFooter(Class<?> stackClass, Class<?> expandableClass)
            throws ReflectiveOperationException {
        notificationFooter = new NotificationFooter(stackClass, expandableClass);
        Class<?> shelfClass = stackClass.getField("mShelf").getType();
        // Samsung also refreshes shelf visibility from its separate running-animation updater,
        // after the main state consumer. Keep that refresh on the same model pose as well.
        hooks.findAndHookMethod(shelfClass, "updateAppearance", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                try {
                    View stack = nssl();
                    if (stack != null && ownsNotificationGeometry(stack)
                            && notificationFooter.isShelf(stack, param.thisObject))
                        notificationFooter.render(stack, notificationRows.model, true);
                } catch (Throwable t) { log(t); }
            }
        });
        XposedBridge.log(Entry.TAG + ": notification footer motion hook installed");
    }

    private void installNotificationGeometry(Class<?> stackClass) throws ReflectiveOperationException {
        notificationGeometry = new NotificationGeometry(stackClass);
        hooks.findAndHookMethod(stackClass, "setExpandedHeight", float.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if ((Float) param.args[0] > 0f && lastMaxDist > 0f
                            && ownsNotificationGeometry(param.thisObject)
                            && notificationGeometry.enter(param, param.thisObject)) {
                        // Preserve native geometry/event processing. Repeated animation frames now
                        // see the same layout height instead of scheduling a complete stack pass.
                        param.args[0] = lastMaxDist;
                    }
                } catch (Throwable t) { log(t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try { notificationGeometry.exit(param); }
                catch (Throwable t) { log(t); }
            }
        });
        hooks.findAndHookMethod(stackClass, "updateStackPosition", boolean.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (ownsNotificationGeometry(param.thisObject)) notificationGeometry.enter(param, param.thisObject);
                } catch (Throwable t) { log(t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try { notificationGeometry.exit(param); }
                catch (Throwable t) { log(t); }
            }
        });
        // Hook the actual captured updater class, rather than all pre-draw listeners in SystemUI.
        Class<?> updater = stackClass.getDeclaredField("mChildrenUpdater").getType();
        hooks.findAndHookMethod(updater, "onPreDraw", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    View stack = nssl();
                    if (stack == null || notificationGeometry.childrenUpdater(stack) != param.thisObject) return;
                    // A QS pull hides the stack from its DOWN (Samsung selects the side there),
                    // before the first height arrives. Defer that first collapsed pass as well.
                    boolean qsOpening = npvc != null && blurDriven && side == SIDE_QS && panel.fraction(npvc) > 0f;
                    if ((qsOpening || qsTouchDown) && npvc != null && panel.barState(npvc) == 0
                            && currentSide(npvc) == SIDE_QS
                            && stack.isAttachedToWindow() && !stack.isShown()
                            && notificationGeometry.canDeferHiddenUpdate(stack)) {
                        // Keep the pending flag AND this pre-draw listener registered. Changed
                        // heights coalesce while QS hides the stack; its first visible traversal
                        // consumes the latest layout. Native events/animations still run above.
                        android.os.Trace.beginSection("ColorMotion.HiddenStackDeferred");
                        param.setResult(Boolean.TRUE);
                        android.os.Trace.endSection();
                    } else if (ownsNotificationGeometry(stack)) notificationGeometry.enter(param, stack);
                } catch (Throwable t) { log(t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    notificationGeometry.exit(param);
                    if (notificationOpening.isWaiting() && !param.hasThrowable()) {
                        View stack = nssl();
                        if (stack != null && notificationGeometry.childrenUpdater(stack) == param.thisObject) {
                            notificationOpening.setHost(stack);
                            notificationOpening.afterTraversal(stack);
                        }
                    }
                } catch (Throwable failure) { failOpen(failure); }
            }
        });
    }

    private boolean ownsNotificationGeometry(Object stack) {
        return !panelSwitching && notificationDriven && side == SIDE_SHADE && npvc != null && panel.barState(npvc) == 0
                && currentSide(npvc) == SIDE_SHADE && stack == nssl();
    }

    private XC_MethodHook createHeightConsumerHook(PanelHeightBinding height) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (heightDispatchDepth != 0) return;
                try {
                    Object callback = param.thisObject;
                    if (interceptPanelHeight(height.panel.get(callback), height.height.getFloat(callback)))
                        param.setResult(null);
                } catch (Throwable failure) { failOpen(failure); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (heightDispatchDepth != 0) return;
                try { afterSetExpandedHeight(height.panel.get(param.thisObject)); }
                catch (Throwable failure) { failOpen(failure); }
            }
        };
    }

    private void installTouchHooks(Class<?> touchHandler) {
        XC_MethodHook touchDispatch = createTouchDispatchHook();
        // Samsung's handleExternalTouch calls onTouchEvent directly. Guarding only the
        // View.OnTouchListener wrapper misses the normal MOVE stream after interception.
        if (hooks.hookAllMethods(touchHandler, "onTouchEvent", touchDispatch).isEmpty()
                || hooks.hookAllMethods(touchHandler, "onInterceptTouchEvent", touchDispatch).isEmpty()) {
            throw new IllegalStateException("Panel touch dispatch hooks missing");
        }
    }

    private XC_MethodHook createTouchDispatchHook() {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Object event = param.args[param.args.length - 1];
                if (!(event instanceof MotionEvent)) return;
                MotionEvent motion = (MotionEvent) event;
                if (motion.getActionMasked() == MotionEvent.ACTION_MOVE) dragDispatchDepth++;
                // Intercept and touch may both receive the same DOWN. Initialize once per
                // physical gesture, and keep that identity through native release retargets.
                if (motion.getActionMasked() == MotionEvent.ACTION_DOWN
                        && motion.getDownTime() != gestureDownTime) {
                    gestureDownTime = motion.getDownTime();
                    gestureInitialized = false;
                    gestureReleased = false;
                    openingReveal = false;
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object event = param.args[param.args.length - 1];
                if (!(event instanceof MotionEvent)) return;
                int action = ((MotionEvent) event).getActionMasked();
                if (action == MotionEvent.ACTION_MOVE) dragDispatchDepth--;
                else if (action == MotionEvent.ACTION_DOWN) {
                    try {
                        qsTouchDown = !failed && npvc != null && panelCollapsed && panel.barState(npvc) == 0
                                && currentSide(npvc) == SIDE_QS;
                    } catch (Throwable t) { qsTouchDown = false; }
                }
                else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    qsTouchDown = false;
                    // Some cancelled/handed-off gestures do not synchronously issue a fling.
                    // Keep the gesture's reveal and saved velocity, but stop its drag clock.
                    stopDrag();
                }
            }
        };
    }

    private XC_MethodHook createFlingHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    beforeFling(param);
                } catch (Throwable t) {
                    log(t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    afterFling(param.thisObject);
                } catch (Throwable t) {
                    log(t);
                } finally {
                    inFling = false;
                    pendingCurve = null;
                    try {
                        afterSetExpandedHeight(param.thisObject);
                    } catch (Throwable t) {
                        log(t);
                    }
                }
            }
        };
    }

    // ---------------------------------------------------------------- expansion fraction

    /** Set to true to trace hook activity in logcat (tag ColorMotion). */
    private static final boolean DEBUG = false;

    static void d(String msg) {
        if (DEBUG) {
            android.util.Log.d(Entry.TAG, msg);
        }
    }

    private void beforeSetExpandedHeight(XC_MethodHook.MethodHookParam param) {
        if (interceptPanelHeight(param.thisObject, (Float) param.args[0])) param.setResult(null);
    }

    /** Shared primitive input path; an inlined native setter needs no temporary hook args. */
    private boolean interceptPanelHeight(Object p, float requested) {
        if (injecting) {
            return false;
        }
        if (DEBUG) {
            d("setExpandedHeightInternal h=" + requested + " bar=" + panel.barState(p)
                    + " tracking=" + panel.tracking(p) + " inFling=" + inFling
                    + " touch=" + calledFromTouchHandler() + " side=" + currentSide(p));
        }
        npvc = p;
        if (panel.barState(p) != 0) {
            stopDrag();
            return false;
        }
        if (panel.side(p) > SIDE_SHADE) return false;
        boolean tracking = panel.tracking(p);
        if (!tracking || inFling) {
            if (dragging && !tracking) {
                stopDrag();
            }
            return false;
        }
        if (!calledFromTouchHandler()) {
            return false;
        }
        float initialOffset = panel.initialOffset(p);
        if (!dragging) {
            // Only first-drag readiness needs geometry here. MOVE samples merely retarget
            // the spring; applyFraction queries current native geometry for each height
            // update. Repeating the full native height calculation at input frequency
            // adds scans without making the next applied height any fresher.
            float maxDist = maxDistance(p);
            if (maxDist <= 0f) return false;
            lastMaxDist = maxDist;
            startDrag(p, maxDist);
            if (panelClosedOnDown && panel.fraction(p) <= 0f) primeOpening(p);
        }
        float delta = requested - initialOffset;
        QsModel model = motionModel();
        if (delta > 0f && dragStartFraction < 1f) openingReveal = true;
        updateReveal(model, panel.fraction(p));
        float fingerFraction = model.drag(delta, dragStartFraction);
        fraction.spring.response(model.dragResponse(fraction.spring.value));
        startMotionFrames();
        fraction.animateTo(fingerFraction);
        return true;
    }

    private void startDrag(Object p, float maxDist) {
        heightDriven = false;
        dragging = true;
        blurDriven = true;
        side = currentSide(p);
        panelClosedOnDown = panel.closedOnDown(p) || panel.fraction(p) <= 0f;
        trackingStartVelocity = yVelocity(p);
        float current = panel.fraction(p);
        float velocity = 0f;
        if (activeCurve != null) {
            // Keep the momentum at the last native interpolation, including scaled/paused/seeked phase.
            velocity = activeCurve.velocityAtLastSample();
            activeCurve = null;
        }
        fraction.cancel();
        fraction.spring.value = current;
        fraction.spring.velocity = velocity;
        fraction.spring.target = current;
        fraction.spring.damping(1.0).minimumVisibleChange(ColorOsParams.FRACTION_MIN_VISIBLE_CHANGE);
        dragStartFraction = current;
        prepareMotionModel();
        // A panel gesture owns its cards again, even if the previous gesture scrolled the list.
        if (side == SIDE_SHADE) {
            notificationListNative = false;
            captureNotificationScroll();
        }
        if (!gestureInitialized) {
            beginMotionGesture(panelClosedOnDown, trackingStartVelocity);
            openingReveal = panelClosedOnDown;
        } else motionModel().dragStart(panelClosedOnDown, trackingStartVelocity);
        updateReveal(motionModel(), current);
        gestureInitialized = true;
        gestureReleased = false;
        if (side == SIDE_QS) applyQsTranslation(qsRows.model.translation.value);
    }

    /**
     * The spring's first height arrives a frame after this MOVE. Samsung would apply the finger's
     * height now; without it the first layout pass runs collapsed and the first visible frame pays
     * the first-expansion listeners plus a second full stack pass. One pixel starts that work in
     * this (still invisible) frame, before its traversal.
     */
    private void primeOpening(Object p) {
        openingPrimed = true;
        injecting = true;
        try { panel.setHeight(p, 1f); }
        finally { injecting = false; }
    }

    private QsModel motionModel() {
        return side == SIDE_QS ? qsRows.model : notificationRows.model;
    }

    private void beginMotionGesture(boolean closed, float velocity) {
        motionModel().beginGesture(closed, velocity);
        if (side == SIDE_QS) qsRows.beginOpening(closed);
        else {
            captureNotificationScroll();
            if (closed) {
                notificationListNative = false;
                // The same reveal gate must wait for the first native list traversal too.
                // Metadata/rows retained from a prior opening do not establish readiness.
                if (notificationRows.isBound()) {
                    notificationRows.model.setOpeningContentReady(false);
                    notificationOpening.begin(nssl());
                    notifClock.reset();
                }
            }
        }
    }

    private void captureNotificationScroll() {
        notificationScrollKnown = false;
        View stack = nssl();
        if (stack == null || notificationGeometry == null) return;
        try {
            notificationScrollAtStart = notificationGeometry.scrollPosition(stack);
            notificationScrollKnown = true;
        } catch (IllegalAccessException e) { log(e); }
    }

    private void updateReveal(QsModel model, float fraction) {
        if (openingReveal) model.onOpeningFraction(fraction);
        else model.onFraction(fraction);
    }

    private void prepareMotionModel() {
        QsModel m = motionModel();
        m.px = devicePx();
        View root = side == SIDE_QS ? qsView() : nssl();
        if (root != null) {
            int height = root.getMeasuredHeight();
            m.rootHeight = height > 0 ? height : root.getResources().getDisplayMetrics().heightPixels;
            if (side == SIDE_QS) {
                qsMotionView = new WeakReference<>(root);
                qsRows.setMotionHost(root);
            }
        }
        if (side == SIDE_QS) {
            stopNotificationMotion();
            qsRows.prepare();
            qsRows.setArmed(true);
        } else {
            qsRows.setArmed(false);
            releaseStaleQsHost();
            notificationDriven = true;
            notificationOpening.setHost(root);
            if (root != null) {
                try { notificationRows.prepare(root); }
                catch (IllegalAccessException e) { log(e); }
            }
        }
    }

    private void startMotionFrames() {
        if (side == SIDE_QS) qsRows.start();
        else queueNotifFrame();
    }

    private void stopDrag() {
        openingPrimed = false;
        if (dragging) {
            dragging = false;
            fraction.cancel();
        }
    }

    private void applyFraction(float f) {
        Object p = npvc;
        if (p == null) {
            return;
        }
        float maxDist = maxDistance(p);
        if (maxDist <= 0) {
            // Not laid out yet on the first frames after SystemUI starts.
            maxDist = lastMaxDist;
        }
        if (maxDist <= 0) {
            return;
        }
        lastMaxDist = maxDist;
        float height = f * maxDist;
        if (openingPrimed && dragging && height < 1f) height = 1f;
        injecting = true;
        try {
            panel.setHeight(p, height);
        } finally {
            injecting = false;
        }
    }

    private void afterSetExpandedHeight(Object p) {
        npvc = p;
        if (panel.barState(p) != 0) {
            if (panelSlide != null) panelSlide.abort();
            panelSwitching = false;
            qsRows.setArmed(false);
            stopNotificationMotion();
            return;
        }
        // Samsung has already computed this with the exact maximum used for this height.
        // Recomputing getMaxPanelHeight here scans native geometry/notification state again.
        float raw = panel.fraction(p);
        int nativeSide = panel.side(p);
        // Native state 2 means both panels are sliding, 3 means neither panel is active.
        // Neither is a notification opening. Its own final slide consumer owns that handoff.
        if (nativeSide > SIDE_SHADE || panelSwitching) {
            if (raw <= 0f) {
                panelCollapsed = true;
                if (nativeSide == 3) {
                    if (panelSlide != null) panelSlide.abort();
                    panelSwitching = false;
                    stopDrag();
                    activeCurve = pendingCurve = null;
                    qsRows.snapHidden();
                    qsRows.setArmed(false);
                    stopNotificationMotion();
                    gestureInitialized = gestureReleased = heightDriven = false;
                    releaseBlurIfSettled();
                }
            }
            return;
        }
        if (raw > 0f && panelCollapsed && gestureInitialized && openingReveal && nativeSide != side) {
            // A fast external/focus-transfer gesture can initialize motion before Samsung
            // finalizes its side. This is still the first opening, not an open-panel switch.
            float velocity = motionModel().yVelocity;
            boolean released = gestureReleased;
            side = nativeSide;
            prepareMotionModel();
            beginMotionGesture(true, velocity);
            if (released) motionModel().release(velocity, true);
        }
        if (raw > 0f && !panelCollapsed && nativeSide != side) {
            // Some native direct/reversed routes switch the stable side without calling
            // PanelTransitionAnimator.slide. An outgoing curve must not keep its model
            // active or leave the newly shown QS host at its old hidden translation.
            stopDrag();
            fraction.cancel();
            activeCurve = pendingCurve = null;
            inFling = heightDriven = false;
            side = nativeSide;
            prepareMotionModel();
            motionModel().snapShown();
            if (side == SIDE_QS) applyQsTranslation(0f);
            gestureInitialized = gestureReleased = true;
            openingReveal = false;
            blurDriven = true;
            if (splitOpenAnimator != null) applySplitHeaderPosition(splitOpenAnimator);
            startMotionFrames();
        }
        boolean freshOpening = panelCollapsed && raw > 0f;
        panelCollapsed = raw <= 0f;
        if (!dragging && !inFling && activeCurve == null) {
            side = currentSide(p);
        }
        if (raw <= 0f && !dragging && !inFling && activeCurve == null) {
            gestureInitialized = gestureReleased = false;
            heightDriven = false;
        }
        if (freshOpening && !gestureInitialized && !dragging && !inFling && activeCurve == null) {
            // Notification launches, external/programmatic expansions and intercepted tiny
            // pulls can update height without ever entering the tracking MOVE/fling hooks.
            prepareMotionModel();
            beginMotionGesture(true, 0f);
            gestureInitialized = true;
            gestureReleased = true;
            openingReveal = true;
            heightDriven = true;
            blurDriven = true;
            lastHeightFraction = 0f;
        }
        if (heightDriven && !dragging && !inFling && activeCurve == null && raw > 0f) {
            QsModel model = motionModel();
            if (raw < lastHeightFraction) {
                openingReveal = false;
                if (side == SIDE_SHADE) notificationListNative = false;
            }
            model.blurRaw.target = raw;
            model.translation.target = -model.dest() * (1f - QsModel.clamp(raw / 0.86f, 0f, 1f));
        }
        lastHeightFraction = raw;
        if (side == SIDE_QS) {
            stopNotificationMotion();
            qsOnFractionChanged(raw);
        } else {
            shadeOnFractionChanged(raw);
        }
    }

    // ---------------------------------------------------------------- release fling

    private void beforeFling(XC_MethodHook.MethodHookParam param) {
        Object p = param.thisObject;
        npvc = p;
        inFling = false;
        if (panel.barState(p) != 0 || panel.side(p) > SIDE_SHADE || panelSwitching) {
            return;
        }
        heightDriven = false;
        float maxDist = maxDistance(p);
        if (maxDist <= 0) {
            return;
        }
        float vel = (Float) param.args[0];
        lastMaxDist = maxDist;
        boolean expand = (Boolean) param.args[1];
        if (DEBUG) {
            d("flingToHeight vel=" + vel + " expand=" + expand + " target=" + param.args[2] + " dragging=" + dragging);
        }
        float x0 = panel.fraction(p);
        boolean wasDragging = dragging || (gestureInitialized && !gestureReleased);
        float v0 = wasDragging ? fraction.spring.velocity : 0f;
        if (!wasDragging && activeCurve != null) {
            v0 = activeCurve.velocityAtLastSample();
        }
        stopDrag();
        float target = expand ? 1f : 0f;
        int selectedSide = currentSide(p);
        if (side != selectedSide) {
            // finishInputFocusTransfer selects the native side immediately before fling.
            // An earlier MOVE may still have initialized the previous panel's model.
            // Reinitialize only that mismatched model; never replace Samsung's area policy.
            side = selectedSide;
            gestureInitialized = false;
        }
        prepareMotionModel();
        if (side == SIDE_SHADE && !expand) notificationListNative = false;
        QsModel model = motionModel();
        if (!gestureInitialized) {
            panelClosedOnDown = panel.closedOnDown(p) || x0 <= 0f;
            // Tracking and direct native flings enter the same gesture/reveal sequence.
            beginMotionGesture(expand && panelClosedOnDown, vel);
            gestureInitialized = true;
        }
        if (expand) openingReveal = true;
        else if (!expand) openingReveal = false;
        model.release(vel, expand);
        gestureReleased = true;
        updateReveal(model, x0);
        blurDriven = true;
        android.os.Trace.beginSection("ColorMotion.FlingPrepare");
        try {
            pendingCurve = FlingCurve.simulate(x0, v0, target, model::flingResponse,
                    ColorOsParams.FRACTION_MIN_VISIBLE_CHANGE, flingWorkspace);
        } finally { android.os.Trace.endSection(); }
        startMotionFrames();
        inFling = true;
    }

    private FlingCurve pendingCurve;
    private float lastMaxDist;

    /**
     * flingToHeight builds the fling ValueAnimator, hands it to setAnimator() and starts it. setAnimator
     * is small enough that the AOT-compiled SystemUI (compiler filter "speed") inlines it, so the
     * animator is taken over here instead, right after start() and before its first frame.
     */
    private void afterFling(Object p) {
        FlingCurve curve = pendingCurve;
        if (!inFling || curve == null) {
            return;
        }
        Object fling = XposedHelpers.getObjectField(p, "mFlingAnimator");
        Object height = XposedHelpers.getObjectField(p, "mHeightAnimator");
        if (DEBUG) {
            d("afterFling fling=" + fling + " height=" + height + " curveMs=" + curve.durationMs);
        }
        if (!(fling instanceof ValueAnimator) || fling != height) {
            finishImmediateFling(p);
            return;
        }
        ValueAnimator animator = (ValueAnimator) fling;
        if (!animator.isStarted() || animator.getDuration() <= 1) {
            // The card/tile cascade has its own clock even if Samsung finishes the height at once.
            finishImmediateFling(p);
            return;
        }
        zeroOvershoot(animator);
        // Samsung's animator already runs mExpandedHeight -> target (ValueAnimator.ofFloat), which are
        // the curve's endpoints, so only its timing is replaced.
        animator.setInterpolator(curve);
        animator.setDuration(Math.max(1, curve.durationMs));
        activeCurve = curve;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (activeCurve == curve) {
                    activeCurve = null;
                    afterSetExpandedHeight(p);
                }
            }
        });
    }

    private void finishImmediateFling(Object p) {
        updateReveal(motionModel(), panel.fraction(p));
        startMotionFrames();
    }

    /**
     * Samsung's fling update listener (NotificationPanelViewController$$ExternalSyntheticLambda52)
     * adds an over-expansion bounce from its captured floats; ColorOS has no such overshoot.
     */
    private static void zeroOvershoot(ValueAnimator animator) {
        try {
            Field f = ValueAnimator.class.getDeclaredField("mUpdateListeners");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<Object> listeners = (ArrayList<Object>) f.get(animator);
            if (listeners == null) {
                return;
            }
            for (Object l : listeners) {
                if (!l.getClass().getName().startsWith(NPVC + "$$ExternalSyntheticLambda")) {
                    continue;
                }
                for (Field lf : l.getClass().getDeclaredFields()) {
                    if (lf.getType() == float.class) {
                        lf.setAccessible(true);
                        lf.setFloat(l, 0f);
                    }
                }
            }
        } catch (Throwable t) {
            log(t);
        }
    }

    // ---------------------------------------------------------------- notification side

    private boolean ownsNotificationMotion() {
        if (notificationDriven) noticeNotificationScroll(nssl());
        return !panelSwitching && notificationDriven && !notificationListNative && (notificationAnimating || dragging || activeCurve != null)
                && side == SIDE_SHADE && npvc != null
                && panel.barState(npvc) == 0;
    }

    private void noticeNotificationScroll(View stack) {
        if (notificationListNative || stack == null || notificationGeometry == null) return;
        // NSSL can mark a closing/intercepted swipe as being dragged too. That is not an
        // independent list scroll and must not disable the panel's exit animation.
        if (dragging || inFling || activeCurve != null || (gestureInitialized && !gestureReleased)) return;
        if (heightDriven && (panel.fraction(npvc) < 1f || notificationAnimating)) return;
        try {
            int offset = notificationGeometry.scrollPosition(stack);
            if (!notificationScrollKnown) {
                notificationScrollAtStart = offset;
                notificationScrollKnown = true;
            }
            // A retained offset is not a new gesture. A real drag or changing offset
            // still gives Samsung immediate ownership, even during the entrance.
            if (notificationAnimating && offset == notificationScrollAtStart
                    && !notificationGeometry.fingerScrolling(stack)) return;
            if (notificationGeometry.scrolling(stack)) notificationListNative = true;
        }
        catch (IllegalAccessException e) { log(e); }
    }

    private void shadeOnFractionChanged(float f) {
        if (!notificationDriven) return;
        if (f == 0f && !dragging && !inFling && activeCurve == null) {
            stopNotificationMotion();
            releaseBlurIfSettled();
            return;
        }
        updateReveal(notificationRows.model, f);
        queueNotifFrame();
    }

    private void stopNotificationMotion() {
        notificationOpening.cancel();
        cancelNotifFrames();
        blurBackground.clear();
        if (!notificationDriven) return;
        notificationDriven = false;
        notificationAnimating = false;
        View stack = nssl();
        if (stack != null && notificationGeometry != null && npvc != null) {
            try { notificationGeometry.restoreHeight(stack, panel.fraction(npvc) * lastMaxDist); }
            catch (ReflectiveOperationException e) { log(e); }
        }
        notificationRows.model.reset();
        if (splitOpenAnimator != null) {
            for (View v : splitHeaderViews) {
                if (v != null) {
                    v.setScaleX(1f);
                    v.setScaleY(1f);
                }
            }
            try {
                float position = panel.fraction(npvc);
                if (qsScene != null) qsScene.restoreHeaderPose(splitOpenAnimator, position);
                else for (View v : splitHeaderViews) if (v != null) {
                    // Only used before native scene binding is available.
                    v.setAlpha(position == 0f ? 0f : 1f);
                    v.setTranslationY(0f);
                }
            } catch (ReflectiveOperationException e) { log(e); }
        }
        requestNsslUpdate();
    }

    // ---------------------------------------------------------------- control center side

    private boolean canSwitchPanels() {
        return !failed && npvc != null && panel.barState(npvc) == 0 && panel.fraction(npvc) > 0f;
    }

    private void beginPanelSwitch() {
        notificationOpening.cancel();
        panelSwitching = true;
        stopDrag();
        fraction.cancel();
        activeCurve = pendingCurve = null;
        inFling = heightDriven = false;
        cancelNotifFrames();
        notificationAnimating = false;
        qsRows.setArmed(false);
        qsRows.model.snapShown();
        notificationRows.model.snapShown();
        notificationListNative = false;
        qsRows.setArmed(true);
        applyQsTranslation(0f);
        requestNsslUpdate();
    }

    private void afterPanelSlide(Object animator, Object[] args) throws ReflectiveOperationException {
        if (!panelSlide.allowed(animator)) {
            if (panelSwitching) {
                panelSlide.abort();
                panelSwitching = false;
                qsRows.setArmed(false);
                stopNotificationMotion();
            }
            return;
        }
        int state = panelSlide.state(animator);
        if (state == 2 && panelSlide.isPanelTransition(args) && (Float) args[1] == 0f) {
            if (!panelSwitching) beginPanelSwitch();
            if (panelSlide.isHorizontal(args)) panelSlide.apply(animator, args);
        } else if ((state == SIDE_QS || state == SIDE_SHADE) && (panelSwitching
                || (!panelCollapsed && side != state && panel.fraction(npvc) >= 0.99f))) {
            // An immediate/vertical native side change may finish without another height
            // callback. Adopt its side as well, retaining its native transition style.
            if (!panelSwitching) beginPanelSwitch();
            panelSlide.reset();
            panelSwitching = false;
            side = state;
            prepareMotionModel();
            motionModel().snapShown();
            openingReveal = false;
            gestureInitialized = gestureReleased = true;
            panelCollapsed = false;
            blurDriven = true;
            startMotionFrames();
        }
    }

    /** QSPanelExpandFraction.onFractionChanged -> header / tile-row visibility. */
    private void qsOnFractionChanged(float f) {
        qsRows.setArmed(true);
        if (f == 0f && !dragging && !inFling && activeCurve == null) {
            qsRows.snapHidden();
            trackingStartVelocity = 0f;
            releaseBlurIfSettled();
            return;
        }
        updateReveal(qsRows.model, f);
        qsRows.start();
    }

    private void applyQsTranslation(float ty) {
        View qs = qsMotionView.get();
        if (qs == null || !qs.isAttachedToWindow()) {
            qs = qsView();
            qsMotionView = new WeakReference<>(qs);
        }
        if (qs != null) {
            qsRows.setMotionHost(qs);
            qsRows.clipping.prepareHosts(qs);
            if (qs.getTranslationY() != ty) qs.setTranslationY(ty);
        }
    }

    private void afterSplitOpenPosition(Object animator) {
        if (splitOpenAnimator != animator || splitHeaderViews[0] == null) captureSplitViews(animator);
        applySplitHeaderPosition(animator);
    }

    private void captureSplitViews(Object animator) {
        splitOpenAnimator = animator;
        if (splitViewBinding == null || splitViewBinding.owner != animator.getClass())
            splitViewBinding = new SplitViewBinding(animator.getClass());
        splitHeaderViews[0] = splitViewBinding.read(animator, 0);
        splitHeaderViews[1] = splitViewBinding.read(animator, 1);
        splitHeaderViews[2] = qsScene != null && qsScene.hasSeparateButtons()
                ? splitViewBinding.read(animator, 2) : null;
        splitBlurViews[0] = splitViewBinding.read(animator, 3);
        splitBlurViews[1] = splitViewBinding.read(animator, 4);
        splitBlurViews[2] = splitViewBinding.read(animator, 5);
        for (int i = 0; i < splitHeaderViews.length; i++) {
            nestedHeader[i] = false;
            for (int j = 0; j < splitHeaderViews.length; j++)
                if (i != j && isDescendant(splitHeaderViews[i], splitHeaderViews[j])) nestedHeader[i] = true;
        }
    }

    private static boolean isDescendant(View child, View parent) {
        if (child == null || parent == null || child == parent) return false;
        android.view.ViewParent p = child.getParent();
        for (int depth = 0; p != null && depth < 32; depth++, p = p.getParent()) {
            if (p == parent) return true;
        }
        return false;
    }

    /**
     * The header clock lives inside the QS fragment's view. When a QS opening collapses, snapHidden()
     * leaves that host translated to its hidden offset; the notification shade never reset it, so
     * the next notification opening showed the clock lifted against the carrier row.
     */
    private void releaseStaleQsHost() {
        View host = qsMotionView.get();
        if (host == null) {
            host = qsView();
            if (host != null) qsMotionView = new WeakReference<>(host);
        }
        if (host != null && host.getTranslationY() != 0f) host.setTranslationY(0f);
    }

    private void applySplitHeaderPosition(Object animator) {
        if (npvc == null || panel.barState(npvc) != 0 || panelSwitching || panel.side(npvc) > SIDE_SHADE) return;
        if (side == SIDE_SHADE) releaseStaleQsHost();
        if (side == SIDE_SHADE && !notificationDriven) return;
        if (panel.fraction(npvc) == 0f && !dragging && !inFling) return;
        QsModel model = motionModel();
        float ty = model.translation.value;
        if (side == SIDE_SHADE) ty += model.headerTranslation();
        for (int i = 0; i < splitHeaderViews.length; i++) {
            View v = splitHeaderViews[i];
            if (v != null) {
                // Both sides can contain nested native header views. QS also has its
                // Compose header inside the translated native content host.
                boolean nested = nestedHeader[i] || (side == SIDE_QS && isDescendant(v, qsMotionView.get()));
                float y = nested ? 0f : ty;
                if (v.getTranslationY() != y) v.setTranslationY(y);
                float alpha = nested ? 1f : model.headerOpacity();
                if (v.getAlpha() != alpha) v.setAlpha(alpha);
                if (side == SIDE_SHADE) {
                    float scale = nested ? 1f : model.headerScale.value;
                    if (v.getScaleX() != scale) v.setScaleX(scale);
                    if (v.getScaleY() != scale) v.setScaleY(scale);
                }
            }
        }
        for (View v : splitBlurViews) {
            if (v != null && v.getTranslationY() != 0f) {
                v.setTranslationY(0f);
            }
        }
    }

    // ---------------------------------------------------------------- blur

    /**
     * Both surfaces use the reference QS blur curve with the minimum opening duration.
     * Returns null to keep Samsung's value (mirror, bouncer, lock screen, or not driven by us).
     */
    private Float colorOsBlurFraction(Object controller) {
        float value = ownedBlurFraction(controller);
        return Float.isNaN(value) ? null : value;
    }

    /** Primitive hot path; NaN hands the complete native operation back to Samsung. */
    private float ownedBlurFraction(Object controller) {
        if (!blurDriven || npvc == null || panel.barState(npvc) != 0) {
            return Float.NaN;
        }
        try {
            if (mirrorVisible.getBoolean(controller) || bouncerShowing.getBoolean(controller)) return Float.NaN;
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        // A disarmed surface can retain an old full-blur spring. Taking the maximum would let
        // that stale value bypass the active panel's opening clock, especially on notifications.
        return motionModel().blurFraction();
    }

    /** Runs Samsung's blur frame (doBlur + background alpha) with the current ColorOS fraction. */
    private void requestBlurFrame() {
        Object c = blurController;
        if (c == null || !blurDriven) {
            return;
        }
        try {
            float value = ownedBlurFraction(c);
            if (Float.isNaN(value) || value == lastRequestedBlur) return;
            android.os.Trace.beginSection("ColorMotion.Blur");
            try {
                blurFrame.request(c);
                lastRequestedBlur = value;
            }
            finally { android.os.Trace.endSection(); }
        } catch (Throwable t) {
            log(t);
        }
    }

    /** Hands the blur back to Samsung once the panel has collapsed and both springs are at rest. */
    private void releaseBlurIfSettled() {
        notificationRows.model.blurRaw.snapTo(0f);
        qsRows.model.blurRaw.snapTo(0f);
        requestBlurFrame();
        blurDriven = false;
        blurFrameGate.reset();
        lastRequestedBlur = Float.NaN;
    }

    private boolean isMotionRunning() {
        // Coalesce only while an element clock advances the frame epoch. A settled
        // held finger can leave dragging=true without any model callbacks; its later
        // native blur invalidations must still run even at an unchanged fraction.
        return notificationAnimating || qsRows.isRunning();
    }

    // ---------------------------------------------------------------- notification frame clock

    private void queueNotifFrame() {
        notificationAnimating = true;
        if (!notifFrameQueued) {
            if (notifRun == null) notifRun = new NotificationRun();
            notifFrameQueued = true;
            android.view.Choreographer.getInstance().postFrameCallback(notifRun);
        }
    }

    private void cancelNotifFrames() {
        if (notifRun != null) android.view.Choreographer.getInstance().removeFrameCallback(notifRun);
        notifRun = null;
        notifFrameQueued = false;
        notifClock.reset();
    }

    private void requestNsslUpdate() {
        View nssl = nssl();
        if (nssl != null) {
            XposedHelpers.callMethod(nssl, "requestChildrenUpdate");
        }
    }

    private float devicePx() {
        Context ctx = context();
        return ctx == null ? 1f : ctx.getResources().getDisplayMetrics().widthPixels / QsModel.COLOROS_WIDTH_PX;
    }

    // ---------------------------------------------------------------- helpers

    private int currentSide(Object p) {
        return panel.side(p);
    }

    /** MOVE in either direct onTouchEvent or intercept feeds the spring; terminal events stay native. */
    private boolean calledFromTouchHandler() {
        return dragDispatchDepth > 0;
    }

    private float maxDistance(Object p) {
        return panel.maxDistance(p);
    }

    private float yVelocity(Object p) {
        try {
            VelocityTracker vt = (VelocityTracker) panel.velocityTracker(p);
            vt.computeCurrentVelocity(1000);
            return vt.getYVelocity();
        } catch (Throwable t) {
            return 0f;
        }
    }

    private View nssl() {
        View cached = notificationStack.get();
        if (notificationStackPanel.get() == npvc && cached != null && cached.isAttachedToWindow()) return cached;
        try {
            Object controller = XposedHelpers.getObjectField(npvc, "mNotificationStackScrollLayoutController");
            View view = (View) XposedHelpers.getObjectField(controller, "mView");
            notificationStack = new WeakReference<>(view);
            notificationStackPanel = new WeakReference<>(npvc);
            return view;
        } catch (Throwable t) {
            return null;
        }
    }

    private View qsView() {
        try {
            Object qsController = XposedHelpers.getObjectField(npvc, "mQsController");
            Object qs = XposedHelpers.getObjectField(qsController, "mQs");
            return qs == null ? null : (View) XposedHelpers.callMethod(qs, "getView");
        } catch (Throwable t) {
            return null;
        }
    }

    private Context context() {
        View root = (View) callOrNull(npvc, "getView");
        return root == null ? null : root.getContext();
    }

    private static Object callOrNull(Object o, String method) {
        if (o == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(o, method);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** MonoSpline$$ExternalSyntheticOutline0.m(max, min, t, max) = max - (max - min) * t. */
    private static float lerpResponse(float max, float min, float t) {
        return max - (max - min) * t;
    }

    private static void log(Throwable t) {
        XposedBridge.log(Entry.TAG + ": " + t);
    }
}
