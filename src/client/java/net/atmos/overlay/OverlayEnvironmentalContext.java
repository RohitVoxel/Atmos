package net.atmos.overlay;

import net.atmos.seasonal.SeasonalFeelingSnapshot;

/**
 * Frame-global environmental values shared by every behaviour evaluator in
 * one simulation pass. Built once per overlay tick (AtmosClient) and passed
 * down — never recomputed per surface, never per chunk.
 */
public record OverlayEnvironmentalContext(
        float nightDepth,
        float thermalEnergy,
        float humidityMass,
        float rainLevel,
        SeasonalFeelingSnapshot seasonal
) {
    public OverlayEnvironmentalContext {
        if (seasonal == null) throw new IllegalArgumentException("seasonal must not be null");
    }
}