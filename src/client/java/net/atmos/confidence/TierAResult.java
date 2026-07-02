package net.atmos.confidence;

/**
 * Explainable breakdown of a Tier A (Atmospheric Possibility) evaluation.
 *
 * Per Chapter 4 §9 ("Confidence Is Explainable") and the Debug Overlay
 * requirements in §20, every confidence value must be decomposable into its
 * contributing factors without recomputation. This record exists so a future
 * debug overlay can display exactly which factor drove a given Tier A result,
 * even though no overlay UI is built in this task.
 */
public record TierAResult(
        float humidityFactor,
        float thermalFactor,
        float value
) {}