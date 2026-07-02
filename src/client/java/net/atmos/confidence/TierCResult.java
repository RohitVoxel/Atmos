package net.atmos.confidence;

/**
 * Explainable breakdown of a Tier C (Geometric Presentation) evaluation.
 * See TierAResult's class doc for the general explainability rationale.
 */
public record TierCResult(
        float distanceFactor,
        float alignmentFactor,
        float frustumFactor,
        float value
) {}