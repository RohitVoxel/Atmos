package net.atmos.exposure;

/**
 * Explainable breakdown of one Environmental Luminance evaluation — Chapter 14 §14.6.
 * Directional lighting (SunReach) is intentionally absent — see
 * EnvironmentalLuminanceEvaluator's class doc.
 */
public record EnvironmentalLuminanceResult(
        float globalIlluminance,
        float localOpenness,
        float visualSalienceDamping,
        float value
) {}