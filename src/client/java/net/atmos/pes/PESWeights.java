package net.atmos.pes;

/**
 * Centralized tuning constants for Chapter 12 Stage 1/2 evaluators.
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
}