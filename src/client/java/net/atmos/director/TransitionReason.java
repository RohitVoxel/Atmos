package net.atmos.director;

/**
 * Explainable cause of the Atmosphere Director's current phase, per
 * Chapter 11 §11.19/§11.20's "Reason For Transition" debug field.
 */
public enum TransitionReason {
    NONE,
    BIOME_CHANGE,
    TIER_A_BELOW_CALM_THRESHOLD,
    TIER_A_ABOVE_PEAK_CANDIDATE_THRESHOLD,
    TIER_A_RISING,
    TIER_A_FALLING,

    /**
     * Chapter 11 §11.22 — "If HeroScore > 0.85 the Director enters Hero
     * Mode," combined with the Tier A > 0.75 Peak Candidate condition
     * already gating this branch (§11.18). Stage 3 addition.
     */
    HERO_MOMENT_PEAK_ENTERED
}