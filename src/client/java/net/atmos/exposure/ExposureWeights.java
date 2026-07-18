package net.atmos.exposure;

/**
 * Centralized tuning constants for Chapter 14 Exposure Model logic.
 * No Exposure Model class may declare its own tuning constant.
 *
 * EXPOSURE_BASELINE is the only value Chapter 14 mandates directly (the
 * multiplicative identity, §14.6). Every other constant below is
 * implementation-defined pending explicit tuning sign-off — the same
 * flagged-but-shipped status already used by ConfidenceWeights.
 *
 * Deliberately absent: any weight combining globalFactor and
 * directionalFactor (Appendix W §1's Environmental Luminance Estimate).
 * Appendix W §8 leaves that aggregation operator an unresolved Architect
 * decision — a tuning constant here would silently make that decision.
 * See EnvironmentalLightingFactorEvaluator / TargetExposureEvaluator.
 */
public final class ExposureWeights {

    private ExposureWeights() {}

    /** Multiplicative identity — "no adaptation applied" (§14.6). */
    public static final float EXPOSURE_BASELINE = 1.0f;

    // --- Target Exposure anchors — reused verbatim per
    // TargetExposureEvaluator's own doc. Appendix W §8 lists numeric
    // reuse of these anchors as "Unresolved."
    public static final float EXPOSURE_SCALE_BRIGHT_ANCHOR = 0.65f;
    public static final float EXPOSURE_SCALE_DARK_ANCHOR   = 1.70f;

    // Residual Atmospheric Memory bias ceiling — no numeric anchor exists
    // in Chapter 13/14 for this magnitude; implementation-defined.
    public static final float MEMORY_BIAS_MAX = 0.25f;

    // --- Temporal Adaptation (§14.9 asymmetry) ---
    // Dark adaptation (exposureScale rising) is slow; bright adaptation
    // (exposureScale falling) is fast — reuses FogDrifter's existing
    // build/clear asymmetry idiom rather than inventing a new one.
    public static final float EXPOSURE_DRIFTER_BUILD_SPEED = 0.6f;
    public static final float EXPOSURE_DRIFTER_CLEAR_SPEED = 1.8f;

    // --- Movement-Speed Adaptation Scaling (Stage 3, §14.9) ---
    // Satisfies only the adaptation-speed directive of §14.9. §14.25's
    // separate Predictive Exposure capability (pre-adapting ahead of an
    // anticipated environment based on travel direction/terrain lookahead)
    // is not implemented here and is not claimed by this stage.
    // Threshold magnitudes mirror FogInterpolator/FogContext's own
    // walk/fast speed bands (~4-5 and ~18-20 blocks/sec).
    public static final float ADAPTATION_SPEED_WALK_THRESHOLD = 5.0f;
    public static final float ADAPTATION_SPEED_FAST_THRESHOLD = 20.0f;

    public static final float ADAPTATION_SCALE_WALK   = 1.0f;
    public static final float ADAPTATION_SCALE_ELYTRA = 2.2f;
}