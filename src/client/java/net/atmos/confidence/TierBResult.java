package net.atmos.confidence;

/**
 * Explainable breakdown of a Tier B (Local Opportunity) evaluation.
 * See TierAResult's class doc for the general explainability rationale.
 */
public record TierBResult(
        float terrainOpennessFactor,
        float skyExposureFactor,
        float value
) {}