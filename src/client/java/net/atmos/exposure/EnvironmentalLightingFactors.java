package net.atmos.exposure;

/**
 * Raw, unaggregated lighting factors feeding Chapter 14 §14.6
 * (Environmental Luminance) — Appendix W §1.
 *
 * globalFactor      — Global Illumination State term (thermalEnergy proxy).
 * directionalFactor — Directional Lighting term: the position-independent
 *                      Solar Position sub-term of SunReach Stage One
 *                      (Chapter 8), added in Stage 3.
 *
 * Deliberately NOT combined into a single scalar. Appendix W §1.2 leaves
 * the aggregation operator (weighted sum vs. geometric mean vs. product,
 * etc.) an unresolved Architect decision (Appendix W §8, "ELE Aggregation
 * Operator: Pending"). Choosing one here would make that decision inside
 * this data record rather than leaving it to whichever future stage is
 * authorized to resolve it. Consumers read the two factors individually
 * — see TargetExposureEvaluator's class doc for the current, deliberately
 * partial consumption of this record.
 *
 * Local Cell State (Appendix W §1's third named input) is absent —
 * identical precedent to RawExposureFactors' own documented CellGrid
 * omission.
 */
public record EnvironmentalLightingFactors(
        float globalFactor,
        float directionalFactor
) {}