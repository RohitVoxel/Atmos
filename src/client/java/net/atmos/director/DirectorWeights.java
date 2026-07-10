package net.atmos.director;

/**
 * Centralized tuning constants for Chapter 11 Atmosphere Director logic,
 * mirroring the ConfidenceWeights / ClusterConstants / CompositionWeights
 * pattern — no Director evaluation logic may declare its own tuning
 * constant.
 *
 * --- Anchored values (Chapter 11 §11.18) ---
 *
 * CALM_THRESHOLD and PEAK_CANDIDATE_THRESHOLD are taken directly from
 * §11.18's worked "Transition Rules": "Tier A < 0.20 → Calm",
 * "Tier A > 0.75 → Peak Candidate".
 *
 * --- Implementation-defined value ---
 *
 * TIER_A_TREND_EPSILON has no anchor anywhere in Chapter 11 — see prior
 * revision's doc for the hysteresis/dead-band rationale.
 *
 * --- Stage 3 addition (Chapter 11 §11.22) ---
 *
 * HERO_MOMENT_THRESHOLD is taken directly from §11.22's own worked text:
 * "If HeroScore > 0.85 the Director enters Hero Mode." This is the only
 * numeric anchor §11.22 supplies for Hero Moment gating.
 *
 * --- Stage 4 addition (Chapter 11 §11.25) ---
 *
 * VISUAL_FATIGUE_PEAK_INCREASE_RATE and VISUAL_FATIGUE_CALM_DECREASE_RATE
 * are taken directly from §11.25's own worked "Fatigue Recovery" text:
 * "Peak → Fatigue +0.003/sec", "Calm → Fatigue -0.001/sec." These are the
 * only two numeric anchors §11.25 supplies. No rate is anchored for
 * BUILDING, RESOLVING, or ESTABLISHING — see AtmosphereDirector's class
 * doc for the resulting implementation-defined "hold steady" decision.
 *
 * --- Stage 5 addition (Chapter 11 §11.26, Appendix Q) ---
 *
 * GLOBAL_INTENSITY_BASELINE and GLOBAL_INTENSITY_PEAK_BOOST_MAX are taken
 * directly from Appendix Q, the authoritative clarification of §11.26's
 * previously unspecified mathematics:
 *   - Q.3 fixes the neutral multiplier identity at 1.0.
 *   - Q.2 Principle 2 / Chapter 11 §11.26 fixes the maximum Peak bonus at
 *     "increases overall atmospheric intensity by 12%" — i.e. +0.12.
 * No other coefficient, curve, or smoothing factor is introduced, per
 * Appendix Q §Q.9 ("No Hidden Logic").
 *
 * --- Stage 6 addition (Chapter 11 §11.27, Appendix R) ---
 *
 * EMOTIONAL_RHYTHM_SPEED and the five EMOTIONAL_RHYTHM_TARGET_* constants
 * are taken directly from Appendix R, the authoritative clarification of
 * §11.27's previously unspecified mathematics:
 *   - R.5 fixes the phase→target mapping (CALM=0.00, ESTABLISHING=0.20,
 *     BUILDING=0.60, PEAK=1.00, RESOLVING=0.30).
 *   - R.7 fixes the convergence speed at 0.005/sec.
 * No oscillation, noise, or alternative curve is introduced, per R.13's
 * requirement that Emotional Rhythm remain a pure first-order convergence
 * independent of Hero Moment, Visual Fatigue, and Global Intensity.
 *
 * {@link #emotionalRhythmTarget(DirectorPhase)} centralizes the phase→
 * target lookup here alongside its source constants, mirroring how
 * ConfidenceWeights/ClusterConstants/CompositionWeights keep all tuning
 * data — including simple lookup logic derived directly from that data —
 * in one place rather than embedding it inside AtmosphereDirector.
 */
public final class DirectorWeights {

    private DirectorWeights() {}

    public static final float CALM_THRESHOLD = 0.20f;

    public static final float PEAK_CANDIDATE_THRESHOLD = 0.75f;

    public static final float TIER_A_TREND_EPSILON = 0.02f;

    /** Chapter 11 §11.22 — "If HeroScore > 0.85 the Director enters Hero Mode." */
    public static final float HERO_MOMENT_THRESHOLD = 0.85f;

    /** Chapter 11 §11.25 — "Peak → Fatigue +0.003/sec." */
    public static final float VISUAL_FATIGUE_PEAK_INCREASE_RATE = 0.003f;

    /** Chapter 11 §11.25 — "Calm → Fatigue -0.001/sec." */
    public static final float VISUAL_FATIGUE_CALM_DECREASE_RATE = 0.001f;

    /** Appendix Q §Q.3 — neutral Global Intensity multiplier identity. */
    public static final float GLOBAL_INTENSITY_BASELINE = 1.0f;

    /**
     * Appendix Q §Q.2 Principle 2 / Chapter 11 §11.26 — "Peak increases
     * overall atmospheric intensity by 12%." Maximum additive bonus
     * available before Visual Fatigue attenuation.
     */
    public static final float GLOBAL_INTENSITY_PEAK_BOOST_MAX = 0.12f;

    /**
     * Appendix R §R.7 — Emotional Rhythm convergence speed, per second.
     * Chosen so convergence occurs over several minutes rather than
     * seconds, per §11.27's "measured in minutes, not seconds."
     */
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

    /**
     * Appendix R §R.5 phase-to-target mapping. Exhaustive over
     * {@link DirectorPhase}; no default branch permitted.
     */
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