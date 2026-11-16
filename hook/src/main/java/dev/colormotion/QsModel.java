package dev.colormotion;

import java.util.Arrays;

/**
 * Control-center motion model (reference: com.oplus.systemui.plugins.qs.animator), free of Android
 * dependencies so it can be replayed offline against reference recordings.
 *
 * Sources: QSPanelExpandFraction (fraction + element thresholds), QSPanelTranslationY (panel
 * translation), QSPanelAnimatorManager.setFirst/SecondElementVisible + QSElementAnimator (header and
 * row springs), PullAnimationProxy.negativeSpringOffsetY (row offsets), QsElementsChain (overscroll
 * stretch and Rebound chain).
 *
 * Distances in ColorOS are raw pixels on a 1272 px wide panel; {@link #px} rescales them to the
 * device so the motion keeps the same proportions on screen.
 */
final class QsModel {
    static final int MAX_ROWS = 24;
    static final float COLOROS_WIDTH_PX = 1272f;

    /** Device pixels per ColorOS pixel. */
    float px = 1f;
    /** QSPanelTranslationY.destTranslationY in ColorOS px (layout dependent, default 102). */
    float destColorOs = ColorOsParams.QS_DEST_TRANSLATION_PX;
    /** Row index within its component (PullAnimationProxy negativeSpringOffsetY). */
    int[] subIndexOfRow = new int[MAX_ROWS];
    /**
     * Node index of each row: ColorOS counts grid cell rows, so a two-cell-tall block advances the
     * index by two. Drives start scale, spring response, fade delay and chain position.
     */
    int[] nodeOfRow = new int[MAX_ROWS];

    // ---- blur (QSPanelExpandFraction.blurFractionAnimation: damping 1, response 0.35, mvc 0.001)
    final Spring blurRaw = new Spring().damping(1.0).response(0.35f).minimumVisibleChange(0.001f);
    private float lastFraction;

    // ---- panel translation (QSPanelTranslationY)
    final Spring translation = new Spring().response(ColorOsParams.QS_TRANSLATION_RESPONSE)
            .bounce(ColorOsParams.QS_TRANSLATION_BOUNCE).minimumVisibleChange(1f);
    private float lastMoveResponse;

    // ---- elements
    final Spring[] alpha = new Spring[MAX_ROWS];
    final Spring[] scale = new Spring[MAX_ROWS];
    final Spring[] neg = new Spring[MAX_ROWS];
    private final long[] showAt = new long[MAX_ROWS];
    private long secondVisibleSince;
    /** Shared opening policy: distance gate, then a timed fade; independent of swipe velocity. */
    static final float OPENING_REVEAL_FRACTION = 0.25f;
    static final long OPENING_FADE_MS = 200L;
    static final long MIN_BLUR_OPEN_MS = 200L;
    private static final float OPENING_ELEMENT_BOUNCE = 0.50f;
    private static final float OPENING_HEADER_BOUNCE = 0.42f;
    private boolean openingPresentation;
    private boolean revealLatched;
    private boolean openingContentReady = true;
    private long blurStartedAt = -1L;
    private long revealStartedAt = -1L;
    private final long[] rowReadyAt = new long[MAX_ROWS];
    final Spring headerAlpha = new Spring().minimumVisibleChange(0.00390625f);
    final Spring headerScale = new Spring().minimumVisibleChange(3.0E-4f);
    int rowCount = 8;
    boolean firstVisible;
    boolean secondVisible;

    // ---- overscroll chain (QsElementsChain)
    final Spring over = new Spring().bounce(0.14f).minimumVisibleChange(1f);
    final float[] overY = new float[MAX_ROWS + 1];
    private final ReboundSpring[] chain = new ReboundSpring[MAX_ROWS + 1];
    private boolean chainActive;
    float rootHeight = 2772f;
    private float lastOverValue = Float.NaN, lastOverPx, lastOverHeight;

    // ---- gesture state
    boolean closedOnDown = true;
    float yVelocity;
    boolean onFling;
    boolean toExpand;
    float curCalcFinalPosition;
    private long nowMs;

    QsModel() {
        for (int i = 0; i < MAX_ROWS; i++) {
            alpha[i] = new Spring().minimumVisibleChange(0.00390625f);
            scale[i] = new Spring().minimumVisibleChange(3.0E-4f);
            neg[i] = new Spring().minimumVisibleChange(3.0E-4f);
            subIndexOfRow[i] = i;
            nodeOfRow[i] = i;
        }
        for (int i = 0; i <= MAX_ROWS; i++) {
            chain[i] = new ReboundSpring();
        }
        reset();
    }

    float dest() {
        return destColorOs * px;
    }

    /** getDefaultSettingEngine(): dest / 0.86. */
    float engine() {
        return dest() / ColorOsParams.QS_ENGINE_DIVISOR;
    }

    /** Finger travel (device px) for the full fraction range: 300 ColorOS px. */
    float dragDistanceForFull() {
        return ColorOsParams.QS_DRAG_PX_FOR_FULL * px;
    }

    // ------------------------------------------------------------------ fraction (QSPanelExpandFraction)

    /** onMove: response 0.2, or 0.35 -> 0.2 over the first 20% after a fast start from closed. */
    float dragResponse(float rawFraction) {
        if (closedOnDown && yVelocity > ColorOsParams.QS_FAST_VELOCITY) {
            float t = clamp(rawFraction / ColorOsParams.QS_FAST_FRACTION_RANGE, 0f, 1f);
            return lerpResponse(ColorOsParams.QS_MAX_FAST_RESPONSE, ColorOsParams.QS_DRAG_RESPONSE, t);
        }
        return ColorOsParams.QS_DRAG_RESPONSE;
    }

    /** onFling + fractionAnimation update listener: per-frame response while flinging. */
    float flingResponse(float rawFraction) {
        float base = toExpand ? ColorOsParams.QS_FLING_EXPAND_RESPONSE : ColorOsParams.QS_FLING_COLLAPSE_RESPONSE;
        if (closedOnDown && yVelocity > ColorOsParams.QS_FAST_VELOCITY) {
            return lerpResponse(ColorOsParams.QS_FLING_FAST_RESPONSE, base,
                    clamp(rawFraction / ColorOsParams.QS_FAST_FRACTION_RANGE, 0f, 1f));
        }
        return base;
    }

    // ------------------------------------------------------------------ gesture input

    /** A new closed-panel gesture must reveal from hidden, including a fling with no drag frames. */
    void beginGesture(boolean closedOnDown, float startVelocity) {
        if (closedOnDown) reset();
        dragStart(closedOnDown, startVelocity);
    }

    void dragStart(boolean closedOnDown, float startVelocity) {
        this.closedOnDown = closedOnDown;
        this.yVelocity = startVelocity;
        this.onFling = false;
        this.toExpand = false;
    }

    /**
     * One finger move. delta = finger travel since touch down (device px), startFraction = panel
     * fraction when the gesture started. Returns the fraction target for the expansion spring.
     */
    float drag(float delta, float startFraction) {
        onFling = false;
        float engine = engine();
        float f2 = engine / dragDistanceForFull();
        float start = startFraction * engine;
        float f3 = delta * f2 + start;
        float target = clamp(f3 / engine, 1.0E-5f, 1f);
        curCalcFinalPosition = target;
        // QSPanelExpandFraction.onMove: blur response 0.35, 0.45 -> 0.35 over the first 20% after a
        // fast start from closed, 0.1 when collapsing right at the top.
        float blurResponse = 0.35f;
        if (closedOnDown && yVelocity > ColorOsParams.QS_FAST_VELOCITY) {
            blurResponse = lerpResponse(0.45f, 0.35f, clamp(blurRaw.value / 0.2f, 0f, 1f));
        } else if (target == 1.0E-5f && lastFraction < 0.015f) {
            blurResponse = 0.1f;
        }
        blurRaw.response(blurResponse);
        blurRaw.target = target;
        translationOnMove(f3);
        // Finger travel past 86% of the engine distance stretches the rows (onMoveOver).
        float f4 = engine * ColorOsParams.QS_ENGINE_DIVISOR;
        float overDistance = start < f4 ? (f3 - f4) / f2 : delta;
        if (overDistance >= 0f) {
            moveOver(overDistance);
        }
        return target;
    }

    void release(float velocity, boolean expand) {
        // Native layout/height handoffs can retarget the same release. Keep the live
        // chain velocities instead of launching the rebound a second time.
        boolean newRelease = !onFling || toExpand != expand;
        onFling = true;
        toExpand = expand;
        if (!expand) {
            closedOnDown = false;
        }
        if (velocity > 0f) {
            yVelocity = velocity;
        }
        curCalcFinalPosition = expand ? 1f : 0f;
        // QSPanelExpandFraction.onFling: blur 0.35 (0.45 for a fast open from closed; the 0.45 collapse
        // value only applies without motion blur, which this device tier has).
        blurRaw.response(expand && closedOnDown && yVelocity > ColorOsParams.QS_FAST_VELOCITY ? 0.45f : 0.35f);
        blurRaw.target = expand ? 1f : 0f;
        // QSPanelTranslationY.onFling
        float response = (closedOnDown && velocity > ColorOsParams.QS_FAST_VELOCITY)
                ? ColorOsParams.QS_TRANSLATION_MAX_RESPONSE : ColorOsParams.QS_TRANSLATION_RESPONSE;
        translation.response(response).bounce(ColorOsParams.QS_TRANSLATION_BOUNCE);
        lastMoveResponse = Float.NaN; // Release reconfigured the spring outside the MOVE cache.
        translation.target = expand ? 0f : -dest();
        if (newRelease) startRebound();
    }

    /** QSPanelTranslationY.onMove. */
    private void translationOnMove(float f3) {
        float dest = dest();
        float value = f3 - (closedOnDown ? dest : engine());
        float target = clamp(value, -dest, 0f);
        float response = ColorOsParams.QS_TRANSLATION_RESPONSE;
        if (yVelocity > ColorOsParams.QS_FAST_VELOCITY) {
            float t = clamp(Math.abs((Math.abs(translation.value) - Math.abs(dest)) / (dest * 0.1f)), 0f, 1f);
            response = lerpResponse(ColorOsParams.QS_TRANSLATION_MAX_RESPONSE, ColorOsParams.QS_TRANSLATION_RESPONSE, t);
        }
        if (response != lastMoveResponse) {
            translation.response(response).bounce(ColorOsParams.QS_TRANSLATION_BOUNCE);
            lastMoveResponse = response;
        }
        translation.target = target;
    }

    // ------------------------------------------------------------------ elements

    /** QSPanelExpandFraction.onFractionChanged. */
    void onFraction(float f) {
        lastFraction = f;
        boolean fast = yVelocity > 1000f && curCalcFinalPosition >= ColorOsParams.QS_SECOND_ELEMENT;
        setFirstElementVisible(f >= (fast ? ColorOsParams.QS_FAST_ELEMENT : 0.5f));
        setSecondElementVisible(f >= (fast ? ColorOsParams.QS_FAST_ELEMENT : ColorOsParams.QS_SECOND_ELEMENT));
    }

    /** Hold elements hidden until the panel reaches the same distance on every opening route. */
    void onOpeningFraction(float f) {
        lastFraction = f;
        if (!openingPresentation) {
            openingPresentation = true;
            if (closedOnDown) blurStartedAt = nowMs;
            // Taking over an already visible panel must not hide or replay its elements.
            if (firstVisible || secondVisible) {
                revealLatched = true;
                revealStartedAt = nowMs - OPENING_FADE_MS - 16L * MAX_ROWS;
            }
        }
        if (!revealLatched && openingContentReady && f >= OPENING_REVEAL_FRACTION) {
            revealLatched = true;
            revealStartedAt = nowMs;
        }
        setFirstElementVisible(revealLatched);
        setSecondElementVisible(revealLatched);
    }

    /** QS first composition can arrive after the native panel has already crossed the gate. */
    void setOpeningContentReady(boolean ready) {
        openingContentReady = ready;
        if (ready && openingPresentation && !revealLatched) onOpeningFraction(lastFraction);
    }

    void noteRowReady(int row) {
        rowReadyAt[row] = nowMs;
    }

    private void setFirstElementVisible(boolean visible) {
        if (firstVisible == visible) {
            return;
        }
        firstVisible = visible;
        headerAlpha.response(visible ? 0.3f : 0.2f).bounce(visible ? 0f : 0.15f);
        headerAlpha.target = visible ? 1f : 0f;
        headerScale.response(0.4f).bounce(visible && openingPresentation ? OPENING_HEADER_BOUNCE : 0.15f);
        headerScale.target = visible ? 1f : 0.72f;
    }

    private void setSecondElementVisible(boolean visible) {
        if (secondVisible == visible) {
            return;
        }
        secondVisible = visible;
        if (visible) secondVisibleSince = nowMs;
        for (int i = 0; i < rowCount; i++) {
            configureRowVisibility(i, visible);
        }
    }

    /** Compose can discover rows after a short flick has already crossed the reveal threshold. */
    void setRowCount(int count) {
        int previous = rowCount;
        rowCount = Math.max(1, Math.min(MAX_ROWS, count));
        for (int i = previous; i < rowCount; i++) configureRowVisibility(i, secondVisible);
    }

    private void configureRowVisibility(int i, boolean visible) {
        int node = nodeOfRow[i];
        float response = ColorOsParams.QS_ELEMENT_SCALE_RESPONSE + 0.05f * (node + 1);
        scale[i].response(response).bounce(visible && openingPresentation
                ? OPENING_ELEMENT_BOUNCE : ColorOsParams.QS_ELEMENT_SCALE_BOUNCE);
        scale[i].target = visible ? 1f : hideScale(node);
        neg[i].response(response).bounce(visible && openingPresentation ? OPENING_ELEMENT_BOUNCE : 0.2f);
        neg[i].target = visible ? 1f : 0f;
        if (visible) {
            showAt[i] = secondVisibleSince + 16L * node;
        } else {
            showAt[i] = -1;
            alpha[i].response(ColorOsParams.QS_ELEMENT_ALPHA_HIDE_RESPONSE).bounce(0f);
            alpha[i].target = 0f;
        }
    }

    static float hideScale(int i) {
        return Math.max(0f, ColorOsParams.QS_ELEMENT_HIDE_SCALE - 0.05f * i);
    }

    // ------------------------------------------------------------------ overscroll chain

    private void moveOver(float distance) {
        chainActive = false;
        float response = 0.3f;
        if (closedOnDown && yVelocity > 1000f) {
            over.minimumVisibleChange(0.001f);
            float t = clamp(over.value / (rootHeight * 0.2f), 0f, 1f);
            response = 0.35f - (0.35f - 0.3f) * t;
        } else {
            over.minimumVisibleChange(1f);
        }
        over.response(response).bounce(0.14f);
        over.target = Math.max(0f, distance);
    }

    private void startRebound() {
        lastOverValue = Float.NaN;
        over.snapTo(0f);
        if (overY[0] <= 0f) {
            chainActive = false;
            Arrays.fill(overY, 0f);
            return;
        }
        chainActive = true;
        for (int i = 0; i <= MAX_ROWS; i++) {
            ReboundSpring s = chain[i];
            if (i == 0) {
                s.origami(150, 30);
            } else {
                s.origami(450, 20);
            }
            s.setCurrentValue(overY[i]);
            s.endValue = overY[i];
            s.restSpeed = 3.0;
            s.restDisplacement = 0.5;
        }
        chain[0].setEndValue(0);
    }

    /** QsElementsChain.setTranslationYOverValue (distances in ColorOS px, rescaled). */
    private void updateOverRows(float fDevice) {
        // A held stretch can settle while row/header springs still request frames. Its
        // geometry is independent of those springs, so reuse it until an input changes.
        if (lastOverValue == fDevice && lastOverPx == px && lastOverHeight == rootHeight) return;
        lastOverValue = fDevice;
        lastOverPx = px;
        lastOverHeight = rootHeight;
        float f = fDevice / px;
        float root = rootHeight / px;
        float sign = Math.signum(f);
        float absolute = Math.abs(f);
        float common = (float) ((1.0 - Math.exp(-f / 120.0)) * 108.0);
        for (int i = 0; i <= MAX_ROWS; i++) {
            float big = 2000f * i + root;
            float stretch = sign * (1f - 1f / ((absolute * (i * 0.02f + 0.25f)) / big + 1f)) * big;
            overY[i] = (stretch + common) * px;
        }
    }

    // ------------------------------------------------------------------ stepping

    /** Disabled system animations finish the entire release, including not-yet-discovered rows. */
    void finishRebound() {
        if (!chainActive) return;
        chainActive = false;
        Arrays.fill(overY, 0f);
        lastOverValue = Float.NaN;
    }

    /** Advances everything by dt milliseconds. Returns true while anything still moves. */
    boolean step(long dtMs) {
        nowMs += dtMs;
        boolean busy = false;
        // Use the existing panel clock. No extra animator, timer or layout invalidation.
        busy |= blurStartedAt >= 0L && nowMs - blurStartedAt < MIN_BLUR_OPEN_MS;
        if (openingPresentation && revealLatched) {
            int lastNode = 0;
            for (int i = 0; i < rowCount; i++) lastNode = Math.max(lastNode, nodeOfRow[i]);
            busy |= nowMs - revealStartedAt < OPENING_FADE_MS + 16L * lastNode;
            for (int i = 0; i < rowCount; i++) busy |= nowMs - rowReadyAt[i] < OPENING_FADE_MS;
        }
        if (onFling && (closedOnDown || !toExpand)) {
            // blurFractionAnimation update listener while flinging.
            float r = 0.35f;
            if (toExpand && yVelocity > ColorOsParams.QS_FAST_VELOCITY) {
                r = lerpResponse(0.45f, 0.35f, clamp(blurRaw.value / 0.2f, 0f, 1f));
            } else if (!toExpand && lastFraction < 0.015f) {
                r = 0.15f;
            }
            blurRaw.response(r);
        }
        busy |= stepOne(blurRaw, dtMs);
        busy |= stepOne(translation, dtMs);
        for (int i = 0; i < rowCount; i++) {
            if (showAt[i] >= 0 && nowMs >= showAt[i]) {
                showAt[i] = -1;
                alpha[i].response(ColorOsParams.QS_ELEMENT_ALPHA_SHOW_RESPONSE).bounce(0f);
                alpha[i].target = 1f;
            }
            busy |= showAt[i] >= 0;
            busy |= stepOne(alpha[i], dtMs);
            busy |= stepOne(scale[i], dtMs);
            busy |= stepOne(neg[i], dtMs);
        }
        busy |= stepOne(headerAlpha, dtMs);
        busy |= stepOne(headerScale, dtMs);
        if (stepOne(over, dtMs) || over.value != 0f) {
            updateOverRows(over.value);
            busy |= over.value != over.target || over.velocity != 0f;
        }
        if (chainActive) {
            // The chain writes overY directly; the next finger stretch must recompute it.
            lastOverValue = Float.NaN;
            boolean chainBusy = false;
            for (int i = 0; i <= MAX_ROWS; i++) {
                chainBusy |= chain[i].advance(dtMs / 1000.0);
                // COUISpringChain.onSpringUpdate: each spring pulls the next one's end value.
                if (i < MAX_ROWS) {
                    chain[i + 1].setEndValue(chain[i].position);
                }
                overY[i] = (float) chain[i].position;
            }
            if (!chainBusy) {
                chainActive = false;
                Arrays.fill(overY, 0f);
            }
            busy |= chainBusy;
        }
        return busy;
    }

    private static boolean stepOne(Spring s, long dt) {
        if (s.value == s.target && s.velocity == 0f) {
            return false;
        }
        return !s.step(dt);
    }

    /** Panel fully collapsed: hidden start state without animation. */
    void reset() {
        for (int i = 0; i < MAX_ROWS; i++) {
            showAt[i] = -1;
            alpha[i].snapTo(0f);
            scale[i].snapTo(hideScale(nodeOfRow[i]));
            neg[i].snapTo(0f);
        }
        headerAlpha.snapTo(0f);
        headerScale.snapTo(0.72f);
        over.snapTo(0f);
        Arrays.fill(overY, 0f);
        lastOverValue = Float.NaN;
        chainActive = false;
        firstVisible = false;
        secondVisible = false;
        translation.snapTo(-dest());
        lastMoveResponse = Float.NaN;
        blurRaw.snapTo(0f);
        openingPresentation = false;
        revealLatched = false;
        openingContentReady = true;
        blurStartedAt = -1L;
        revealStartedAt = -1L;
        Arrays.fill(rowReadyAt, 0L);
        lastFraction = 0f;
        yVelocity = 0f;
        onFling = false;
    }

    // ------------------------------------------------------------------ outputs

    /** Panel-to-panel handoff: content is already open; never launch a second entrance. */
    void snapShown() {
        reset();
        firstVisible = secondVisible = true;
        headerAlpha.snapTo(1f);
        headerScale.snapTo(1f);
        translation.snapTo(0f);
        blurRaw.snapTo(1f);
        lastFraction = 1f;
        closedOnDown = false;
        for (int i = 0; i < MAX_ROWS; i++) {
            alpha[i].snapTo(1f);
            scale[i].snapTo(1f);
            neg[i].snapTo(1f);
        }
    }

    /** Row translation: PullAnimationProxy negativeSpringOffsetY + overTransYMap. */
    float rowTranslation(int row) {
        int node = Math.min(MAX_ROWS, nodeOfRow[row] + 1);
        // Give every row an entrance distance, including the first/tall QS block. This is
        // the existing per-row spring, started at visible reveal rather than finger release.
        int index = subIndexOfRow[row] + (openingPresentation ? 1 : 0);
        return (1f - neg[row].value) * -60f * px * index
                + overY[node];
    }

    float footerOpacity() { return rowAlpha(rowCount - 1); }

    float footerScale() { return 0.75f + 0.25f * headerScale.value; }

    float footerTranslation() {
        int row = rowCount - 1;
        int node = Math.min(MAX_ROWS, nodeOfRow[row] + 1);
        return translation.value + (1f - neg[row].value) * -32f * px + overY[node];
    }

    /** QSPanelExpandFraction.getBlurFraction (motion blur: no 0.02 gate). */
    float blurFraction() {
        float value = clamp(blurRaw.value / 0.86f, 0f, 1f);
        return blurStartedAt < 0L ? value
                : Math.min(value, timedProgress(nowMs - blurStartedAt, MIN_BLUR_OPEN_MS));
    }

    float rowAlpha(int row) {
        float value = openingAlpha(alpha[row].value, 16L * nodeOfRow[row]);
        return openingPresentation ? Math.min(value,
                timedProgress(nowMs - rowReadyAt[row], OPENING_FADE_MS)) : value;
    }

    float headerOpacity() {
        return openingAlpha(headerAlpha.value, 0L);
    }

    private float openingAlpha(float value, long delayMs) {
        value = clamp(value, 0f, 1f);
        if (!openingPresentation) return value;
        if (!revealLatched) return 0f;
        return Math.min(value, timedProgress(nowMs - revealStartedAt - delayMs, OPENING_FADE_MS));
    }

    private static float timedProgress(long elapsedMs, long durationMs) {
        float t = clamp(elapsedMs / (float) durationMs, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    float headerTranslation() {
        return Math.max(overY[0], 0f);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** MonoSpline$$ExternalSyntheticOutline0.m(max, min, t, max) = max - (max - min) * t. */
    static float lerpResponse(float max, float min, float t) {
        return max - (max - min) * t;
    }
}
