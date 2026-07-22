package net.atmos.director;

/**
 * Centralized tuning constants for Chapter 11 Atmosphere Director logic.
 * No Director evaluator may declare its own tuning constant.
 */
public final class DirectorWeights {

    private DirectorWeights() {}

    /** §11.18 — "Tier A < 0.20 → Calm". */
    public static final float CALM_THRESHOLD = 0.20f;

    /** §11.18 — "Tier A > 0.75 → Peak Candidate". */
    public static final float PEAK_CANDIDATE_THRESHOLD = 0.75f;

    /** Implementation-defined hysteresis band — no anchor in Chapter 11. */
    public static final float TIER_A_TREND_EPSILON = 0.02f;

    /** §11.22 — "If HeroScore > 0.85 the Director enters Hero Mode." */
    public static final float HERO_MOMENT_THRESHOLD = 0.85f;

    /** §11.25 — "Peak → Fatigue +0.003/sec." */
    public static final float VISUAL_FATIGUE_PEAK_INCREASE_RATE = 0.003f;

    /** §11.25 — "Calm → Fatigue -0.001/sec." */
    public static final float VISUAL_FATIGUE_CALM_DECREASE_RATE = 0.001f;

    /** Appendix Q §Q.3 — neutral Global Intensity multiplier identity. */
    public static final float GLOBAL_INTENSITY_BASELINE = 1.0f;

    /** Appendix Q §Q.2/§11.26 — max Peak bonus before fatigue attenuation. */
    public static final float GLOBAL_INTENSITY_PEAK_BOOST_MAX = 0.12f;

    /** Appendix R §R.7 — Emotional Rhythm convergence speed, per second. */
    public static final float EMOTIONAL_RHYTHM_SPEED = 0.005f;

    /** Appendix R §R.5 — phase target for {@link DirectorPhase#CALM}. */
    public static final float EMOTIONAL_RHYTHM_TARGET_CALM = 0.00f;

    /** Appendix R §R.5 — phase target for {@link DirectorPhase#ESTABLISHING}. */
    public static final float EMOTIONAL_RHYTHM_TARGET_ESTABLISHING = 0.20f;

    /** Appendix R §R.5 — phase target for {@link DirectorPhase#BUILDING}. */
    public static final float EMOTIONAL_RHYTHM_TARGET_BUILDING = 0.60f;

    /** Appendix R §R.5 — phase target for {@link DirectorPhase#PEAK}. */
    public static final float EMOTIONAL_RHYTHM_TARGET_PEAK = 1.00f;

    /** Appendix R §R.5 — phase target for {@link DirectorPhase#RESOLVING}. */
    public static final float EMOTIONAL_RHYTHM_TARGET_RESOLVING = 0.30f;

    /** Appendix S §S.7 — Director Memory decay rate, per second. Linear only, per §S.17. */
    public static final float HERO_MEMORY_DECAY_RATE = 0.0015f;

    /**
     * Appendix T §T.9, §T.12 — Hero Moment protection floor. heroMultiplier
     * is never allowed to fall below this value regardless of budget.
     */
    public static final float HERO_MULTIPLIER_FLOOR = 0.75f;

    /**
     * Appendix T §T.23–§T.24 — fail-safe budget used whenever no
     * OptimizationPlan is available, or its atmosphereBudget is
     * non-finite. Equivalent to "no reduction requested."
     */
    public static final float OPTIMIZATION_PLAN_FAILSAFE_BUDGET = 1.0f;

    /** Appendix U §U.6 — seconds of continuously unchanged raw weather before it is accepted as stable. */
    public static final float WEATHER_STABILITY_TIME = 5.0f;

    /** Appendix U §U.11 — horizontal speed, in blocks/sec, that triggers Fast Travel Mode. */
    public static final float FAST_TRAVEL_SPEED = 40.0f;

    /** Appendix U §U.12 — evaluation-window scale applied during Fast Travel Mode. */
    public static final float FAST_TRAVEL_SCALE = 0.50f;

    /** Appendix U §U.12 — evaluation-window scale under normal travel speed. */
    public static final float NORMAL_TRAVEL_SCALE = 1.00f;

    /**
     * Appendix ZC §2 — implementation-defined fogDensity weights. Simple
     * weighted sum, clamped to [0,1] by FogDensityEvaluator; weights are
     * not required to sum to 1.0 (this is not a ConfidenceMath weighted
     * geometric product).
     */
    public static final float FOG_DENSITY_HUMIDITY_WEIGHT = 0.65f;
    public static final float FOG_DENSITY_STORM_WEIGHT    = 0.55f;

    /** Appendix R §R.5 phase-to-target mapping. Exhaustive over {@link DirectorPhase}. */
    public static float emotionalRhythmTarget(DirectorPhase phase) {
        return switch (phase) {
            case CALM         -> EMOTIONAL_RHYTHM_TARGET_CALM;
            case ESTABLISHING -> EMOTIONAL_RHYTHM_TARGET_ESTABLISHING;
            case BUILDING     -> EMOTIONAL_RHYTHM_TARGET_BUILDING;
            case PEAK         -> EMOTIONAL_RHYTHM_TARGET_PEAK;
            case RESOLVING    -> EMOTIONAL_RHYTHM_TARGET_RESOLVING;
        };
    }
}