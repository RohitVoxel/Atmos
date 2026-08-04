package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Wetness target evaluation — Batch 2 Phase 2.
 *
 * Continuous rain-exposure weighting (no boolean skyVisible gate) — rain
 * reach scales with the surface's already-computed exposure. When not
 * being actively wetted, the target drops to zero and drying speed is
 * expressed as a rate multiplier driven by heat, low humidity, and sun.
 * Material-specific drying speed (stone slow, wood moderate) is handled by
 * OverlayMaterialProfile's existing per-material clear rates, not here.
 */
public final class OverlayWetnessBehavior {

    private OverlayWetnessBehavior() {}

    public static OverlayTargetResult evaluate(OverlaySurface surface, OverlayEnvironmentalContext ctx) {
        float rainExposureFactor = ctx.rainLevel() * FogMath.lerp(0.15f, 1.0f, surface.exposure());

        if (rainExposureFactor > 0.02f) {
            return new OverlayTargetResult(FogMath.clamp(rainExposureFactor, 0f, 1f) * 10f, 1f);
        }

        float dryingPotential = FogMath.clamp(
                ctx.thermalEnergy() * 0.6f + (1f - ctx.humidityMass()) * 0.4f, 0f, 1f);
        float sunDrying = FogMath.lerp(0.4f, 1.3f, surface.exposure() * (1f - ctx.nightDepth()));
        float dryRateMultiplier = FogMath.clamp(dryingPotential * sunDrying, 0.3f, 2.5f);

        return new OverlayTargetResult(0f, dryRateMultiplier);
    }
}