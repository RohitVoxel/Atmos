package net.atmos.exposure;

/**
 * Explainable breakdown of one Environmental Luminance Estimate (ELE)
 * evaluation — Chapter 14 §14.6, Appendix W §1.
 *
 * globalFactor      — Global Illumination State term (thermalEnergy proxy).
 * directionalFactor — Directional Lighting term (Solar Position, Stage 3).
 * value             — combined ELE in [0,1], consumed by
 *                      {@link TargetExposureEvaluator}.
 *
 * Local Cell State (Appendix W §1's third named input) is absent — see
 * {@link EnvironmentalLuminanceEvaluator}'s class doc.
 */
public record EnvironmentalLuminanceResult(
        float globalFactor,
        float directionalFactor,
        float value
) {}