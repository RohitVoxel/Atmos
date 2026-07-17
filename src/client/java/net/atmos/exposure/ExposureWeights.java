package net.atmos.exposure;

/**
 * Centralized tuning constants for Chapter 14 Exposure Model logic.
 * No Exposure Model class may declare its own tuning constant.
 */
public final class ExposureWeights {

    private ExposureWeights() {}

    /** Identity exposure multiplier — "no adaptation applied." See Stage 1 doc for provenance. */
    public static final float EXPOSURE_BASELINE = 1.0f;

    // --- Stage 2: Environmental Luminance (§14.6) — implementation-defined, no Guide anchor ---
    public static final float HAZE_DIMMING_STRENGTH   = 0.35f;
    public static final float STORM_DARKENING_STRENGTH = 0.55f;
    public static final float LOCAL_OPENNESS_NEUTRAL  = 0.85f;
    public static final float LOCAL_OPENNESS_FLOOR    = 0.55f;
    public static final float VISUAL_SALIENCE_DAMPING_MAX = 0.15f;

    // --- Stage 2: Target Exposure (§14.7) — Chapter 9 §13's own explicit worked-example anchors ---
    public static final float EXPOSURE_SCALE_BRIGHT_ANCHOR = 0.65f;
    public static final float EXPOSURE_SCALE_DARK_ANCHOR   = 1.70f;

    // --- Stage 2: Memory Integration (§14.9) — implementation-defined ---
    public static final float MEMORY_BIAS_MAX = 0.20f;

    // --- Stage 2: Temporal Adaptation asymmetry (§14.7) ---
    // Mapped onto FogDrifter's buildSpeed(rising)/clearSpeed(falling):
    // rising target = darkening = SLOW; falling target = brightening = FAST.
    public static final float DARK_ADAPTATION_SPEED  = 0.35f;
    public static final float BRIGHT_ADAPTATION_SPEED = 1.40f;
}