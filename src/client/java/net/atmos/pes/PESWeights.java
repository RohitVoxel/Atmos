package net.atmos.pes;

/**
 * Centralized tuning constants for Chapter 12 evaluators.
 * No PES evaluator may declare its own tuning constant — mirrors the
 * ConfidenceWeights / DirectorWeights / CompositionWeights convention.
 *
 * No numeric anchor exists anywhere in Chapter 12 for the deviation
 * tolerances below — implementation-defined, the same status as Chapter 8
 * Stage Four/Five/Seven's own documented transfer functions.
 */
public final class PESWeights {

    private PESWeights() {}

    public static final float ENVIRONMENTAL_CONSISTENCY_TOLERANCE = 0.35f;
    public static final float BIOME_IDENTITY_TOLERANCE            = 0.35f;
    public static final float WEATHER_IDENTITY_TOLERANCE          = 0.35f;
    public static final float CATEGORY_PASS_THRESHOLD             = 0.5f;

    /** Trailing frames considered by Temporal Stability and Transition evaluation. */
    public static final int STABILITY_WINDOW_SIZE = 8;

    public static final float TEMPORAL_STABILITY_DELTA_TOLERANCE = 0.12f;

    public static final float TRANSITION_ABRUPT_DELTA_THRESHOLD = 0.25f;
    public static final float TRANSITION_PASS_THRESHOLD         = 0.5f;

    public static final float PATTERN_REPETITION_RATIO_THRESHOLD = 0.6f;
    public static final int   PATTERN_REPETITION_MIN_SAMPLES     = 5;

    /** Appendix C §2's own documented example capacity ("e.g., 300 frames"). */
    public static final int HISTORY_BUFFER_CAPACITY = 300;

    // --- Stage 3: Composition Evaluation (§12.14) ---
    public static final float COMPOSITION_RADIUS_VARIETY_SATURATION    = 0.35f;
    public static final float COMPOSITION_INTENSITY_VARIETY_SATURATION = 0.35f;
    public static final float COMPOSITION_SPACING_VARIETY_SATURATION   = 0.50f;

    // --- Stage 4: Overall Perceptual Score (§12.29) ---
    public static final float OVERALL_WEIGHT_ENVIRONMENTAL_CONSISTENCY = 1.0f;
    public static final float OVERALL_WEIGHT_BIOME_IDENTITY            = 1.0f;
    public static final float OVERALL_WEIGHT_WEATHER_IDENTITY          = 1.0f;
    public static final float OVERALL_WEIGHT_TEMPORAL_STABILITY        = 1.0f;
    public static final float OVERALL_WEIGHT_TRANSITION                = 1.0f;
    public static final float OVERALL_WEIGHT_PATTERN_NON_REPETITION    = 1.0f;
    public static final float OVERALL_WEIGHT_COMPOSITION               = 1.0f;

    public static final float OVERALL_WEIGHT_TOTAL =
            OVERALL_WEIGHT_ENVIRONMENTAL_CONSISTENCY
                    + OVERALL_WEIGHT_BIOME_IDENTITY
                    + OVERALL_WEIGHT_WEATHER_IDENTITY
                    + OVERALL_WEIGHT_TEMPORAL_STABILITY
                    + OVERALL_WEIGHT_TRANSITION
                    + OVERALL_WEIGHT_PATTERN_NON_REPETITION
                    + OVERALL_WEIGHT_COMPOSITION;

    // --- Stage 5: Motion Evaluation (§12.26) ---
    // No numeric anchor exists in §12.26 for either the window size or the
    // "rapid" distance threshold. Threshold is expressed in blocks per
    // history step (Hero anchor displacement), not blocks/second — see
    // MotionEvaluator's class doc for why a real speed unit isn't derivable.
    public static final int   MOTION_WINDOW_SIZE = 8;
    public static final float MOTION_RAPID_TRAVERSAL_THRESHOLD = 24f;
}