package net.atmos.exposure;

/** Explainable breakdown of one Target Exposure evaluation — Chapter 14 §14.7, §14.9. */
public record TargetExposureResult(
        float luminanceExposure,
        float memorySignal,
        float memoryBias,
        float value
) {}