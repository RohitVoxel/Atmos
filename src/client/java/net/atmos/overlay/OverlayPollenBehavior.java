package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Pollen target evaluation — Batch 2 Phase 2.
 *
 * Reuses OverlayManager's already-published POLLEN contribution (Spring
 * season weighting, Phase 1) as the base tendency, then modulates by biome
 * humidity as a vegetation-richness proxy (no separate vegetation density
 * field exists in BiomeTraits) and clears rapidly in rain.
 */
public final class OverlayPollenBehavior {

    private OverlayPollenBehavior() {}

    public static OverlayTargetResult evaluate(OverlaySurface surface, float globalPollenContribution,
                                               OverlayEnvironmentalContext ctx) {
        if (ctx.rainLevel() > 0.02f) {
            return new OverlayTargetResult(0f, FogMath.lerp(1.5f, 4.0f, ctx.rainLevel()));
        }

        float vegetationFactor = FogMath.clamp(surface.humidity(), 0f, 1f);
        float exposureFactor   = FogMath.lerp(0.6f, 1.0f, surface.exposure());

        float target = globalPollenContribution * vegetationFactor * exposureFactor;

        return new OverlayTargetResult(FogMath.clamp(target, 0f, 1f) * 10f, 1f);
    }
}