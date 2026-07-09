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
 */
public final class DirectorWeights {

    private DirectorWeights() {}

    public static final float CALM_THRESHOLD = 0.20f;

    public static final float PEAK_CANDIDATE_THRESHOLD = 0.75f;

    public static final float TIER_A_TREND_EPSILON = 0.02f;

    /** Chapter 11 §11.22 — "If HeroScore > 0.85 the Director enters Hero Mode." */
    public static final float HERO_MOMENT_THRESHOLD = 0.85f;
}