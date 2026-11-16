package dev.colormotion;

/**
 * Motion constants of the reference SystemUI build (PLK110_17.0.0.105, CN).
 * Device tier for that build: persist.sys.oplus.anim_level=1 (my_product/build.prop), so
 * ScrimUtil.isHighGaussianLevel() is true and PlatformBlurManagerImpl.useMotionBlur() is true;
 * NotificationStackScrollLayoutExtImpl therefore picks Config.LEGACY_SCALE_ONLY.
 */
final class ColorOsParams {
    private ColorOsParams() {}

    // ---- Notification shade (com.oplus.systemui.panelanimation.OplusPanelAnimationExImpl) ----

    /** R.dimen.separate_distance_range; getAdjustExpandedHeight: min(d * max / range, max - 1). */
    static final float SHADE_DISTANCE_RANGE_DP = 100f;
    /** PanelExpandedFraction drag stiffness: responseToStiffness(0.2f) (Lambda0 case 2). */
    static final float SHADE_DRAG_RESPONSE = 0.2f;
    /** PanelExpandedFraction.expandedTo(): separate mode -> stiffness 400, damping 1. */
    static final float SHADE_FLING_STIFFNESS = 400f;
    /** PanelExpandedFraction springs use SpringForce().setDampingRatio(1.0f); minimumVisibleChange 0.001. */
    static final float FRACTION_MIN_VISIBLE_CHANGE = 0.001f;

    /** PanelTranslationYValue: responseToStiffness(useMotionBlur ? 0.4f : 0.3f). */
    static final float SHADE_TRANSLATION_RESPONSE = 0.4f;
    /** updatePanelTranslationY (separate mode): fraction / 0.86. */
    static final float SHADE_TRANSLATION_FRACTION_END = 0.86f;
    /** animateToFinalPosition (separate mode): damping = 1 - 0.4 * clamp(vel, 0, 10000) / 10000. */
    static final float SHADE_TRANSLATION_VEL_DAMPING = 0.4f;
    static final float SHADE_TRANSLATION_VEL_MAX = 10000f;
    /** PanelTranslationYValue setMinimumVisibleChange(0.1f). */
    static final float SHADE_TRANSLATION_MIN_VISIBLE_CHANGE = 0.1f;
    /** R.dimen.notification_center_header_top_margin, used when the header geometry is unknown. */
    static final float SHADE_TRANSLATION_FALLBACK_DP = 34f;

    /** updateElementShowing: element shows at 0.86, or 0.2 with isQuicklyElementShow. */
    static final float SHADE_ELEMENT_SHOW = 0.86f;
    static final float SHADE_ELEMENT_SHOW_QUICK = 0.2f;

    // NsslAnimParams.legacyScaleOnly(): (1, 0, 0, 0, 0, 1, 0.9, 0.3, 0.2, 0.16, 0.16, 1, 0.002)
    static final float NSSL_HIDE_SCALE = 0.9f;
    static final float NSSL_SCALE_SHOW_RESPONSE = 0.3f;
    static final float NSSL_SCALE_HIDE_RESPONSE = 0.2f;
    static final float NSSL_SCALE_SHOW_BOUNCE = 0.16f;
    static final float NSSL_SCALE_HIDE_BOUNCE = 0.16f;
    static final float NSSL_MIN_VISIBLE_CHANGE = 0.002f;
    /** NotificationStackScrollLayoutExtImpl.updatePivot(): pivotY -600 without alpha animation. */
    static final float NSSL_PIVOT_Y = -600f;

    // ---- Control center (com.oplus.systemui.plugins.qs.animator) ----

    /** OplusQSRootViewComponent touch move: f3 = rawY * (engine / 300) + start. */
    static final float QS_DRAG_PX_FOR_FULL = 300f;
    /** QSPanelExpandFraction: drag response 0.2, fast-start response 0.35 (maxFastResponse). */
    static final float QS_DRAG_RESPONSE = 0.2f;
    static final float QS_MAX_FAST_RESPONSE = 0.35f;
    /** onFling: expand 0.2, collapse getRawHideResponse() = 0.3; fast expand 0.45 (maxFastBlurResponse). */
    static final float QS_FLING_EXPAND_RESPONSE = 0.2f;
    static final float QS_FLING_COLLAPSE_RESPONSE = 0.3f;
    static final float QS_FLING_FAST_RESPONSE = 0.45f;
    static final float QS_FAST_VELOCITY = 1000f;
    static final float QS_FAST_FRACTION_RANGE = 0.2f;

    /** QSPanelTranslationY: destTranslationY default 102f, response 0.35, bounce 0.15, maxResponse 0.45. */
    static final float QS_DEST_TRANSLATION_PX = 102f;
    static final float QS_TRANSLATION_RESPONSE = 0.35f;
    static final float QS_TRANSLATION_BOUNCE = 0.15f;
    static final float QS_TRANSLATION_MAX_RESPONSE = 0.45f;
    static final float QS_TRANSLATION_MIN_VISIBLE_CHANGE = 1f;
    /** getDefaultSettingEngine(): dest / 0.86. */
    static final float QS_ENGINE_DIVISOR = 0.86f;

    /** QSPanelExpandFraction.onFractionChanged thresholds. */
    static final float QS_SECOND_ELEMENT = 0.86f;
    static final float QS_FAST_ELEMENT = 0.2f;

    /** setSecondElementVisible, node 0: scale from 0.8, response 0.3, bounce 0.25; alpha 0.35 / 0.2. */
    static final float QS_ELEMENT_HIDE_SCALE = 0.8f;
    static final float QS_ELEMENT_SCALE_RESPONSE = 0.3f;
    static final float QS_ELEMENT_SCALE_BOUNCE = 0.25f;
    static final float QS_ELEMENT_ALPHA_SHOW_RESPONSE = 0.35f;
    static final float QS_ELEMENT_ALPHA_HIDE_RESPONSE = 0.2f;
}
