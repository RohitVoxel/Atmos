package net.atmos.pes;

/**
 * Conceptual recommendation vocabulary — Chapter 12 §12.32.
 *
 * PES never modifies rendering, Confidence, SunReach, or
 * EnvironmentalState directly (§12.33, Appendix D §4). These values are
 * purely descriptive flags in a PerceptualReport; consuming systems (the
 * Atmosphere Director, on a future simulation tick per §12.31's
 * one-frame-lagged feed-forward loop) decide independently how, or
 * whether, to act on them.
 */
public enum PerceptualRecommendation {
    /** §12.32 example: "Biome Identity Weak -> Increase environmental coherence." */
    INCREASE_ENVIRONMENTAL_COHERENCE,
    ALIGN_WEATHER_HARMONY,
    /** §12.32 example: "Composition Quality Low -> Improve atmospheric balance." */
    IMPROVE_COMPOSITION_BALANCE,
    SMOOTH_TRANSITIONS,
    INCREASE_VARIATION
}