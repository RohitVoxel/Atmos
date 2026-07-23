package net.atmos.render;

/**
 * Centralized rendering-math constants — Appendix ZB §III. Single
 * authoritative source for every named coefficient shared by the
 * RenderCluster math blockers (Blockers 1-9). No evaluator in this
 * package may redeclare a value listed here as a local constant.
 *
 * Values copied verbatim from Appendix ZB §III — none are invented.
 * Fields marked "reserved" are not yet consumed by any implemented
 * evaluator but are included now so the constants class is populated
 * once, matching Appendix ZB's own single-holder structure, rather than
 * being extended piecemeal across future batches.
 */
public final class RenderingMathConstants {

    private RenderingMathConstants() {}

    // --- Lighting & Shadows ---
    public static final float STORM_LIGHT_ATTENUATION_MAX = 0.7f;
    public static final float SHADOW_HUMIDITY_WEIGHT = 0.4f;
    public static final float SHADOW_STORM_WEIGHT = 0.6f;
    public static final float LUMINANCE_WEIGHT_RED = 0.2126f;
    public static final float LUMINANCE_WEIGHT_GREEN = 0.7152f;
    public static final float LUMINANCE_WEIGHT_BLUE = 0.0722f;

    // --- Tints (Linear RGB, Normalized) ---
    public static final RenderColor DEFAULT_WEATHER_TINT = new RenderColor(1.0f, 1.0f, 1.0f);
    public static final RenderColor STORM_WEATHER_TINT = new RenderColor(0.6f, 0.65f, 0.7f);

    // --- Definition & Softness ---
    public static final float DEFINITION_HUMIDITY_SCALAR = 0.6f;
    public static final float DEFINITION_THERMAL_SCALAR = 0.2f;

    // --- Distance & Fade (reserved — Batch 2 Distance Evaluation) ---
    public static final float FADE_MARGIN_START = 0.7f;

    // --- Animation (reserved — Batch 2 Animation Phase) ---
    public static final float ANIMATION_BASE_SPEED = 1.0f;
    public static final float ANIMATION_STORM_MULTIPLIER = 2.5f;

    // --- LOD & Geometry (Units: Blocks) ---
    public static final float LOD_DISTANCE_STEP = 20.0f;

    /** Architectural invariant per Appendix ZB §III — topology ceiling, must be >= 1. */
    public static final int LOD_MAX_QUADS = 6;

    public static final float WIDTH_OVERLAP_SCALAR = 1.5f;
    public static final float LENGTH_SINE_FLOOR = 0.15f;
    public static final float LENGTH_MAX_ABSOLUTE = 60.0f;

    // --- Alpha Assembly ---
    public static final float ALPHA_SCATTERING_COEFFICIENT = 1.2f;
}