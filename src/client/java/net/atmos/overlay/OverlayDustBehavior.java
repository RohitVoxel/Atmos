package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;

/** Dust target evaluation — Batch 2 Phase 2. Dry, hot, low-humidity, exposed surfaces accumulate dust; rain clears it fast. */
public final class OverlayDustBehavior {

    private OverlayDustBehavior() {}

    public static OverlayTargetResult evaluate(OverlaySurface surface, OverlayEnvironmentalContext ctx) {
        if (ctx.rainLevel() > 0.02f) {
            return new OverlayTargetResult(0f, FogMath.lerp(1.0f, 3.0f, ctx.rainLevel()));
        }

        float drynessFactor = 1f - FogMath.clamp((surface.humidity() + ctx.humidityMass()) * 0.5f, 0f, 1f);
        float heatFactor     = FogMath.clamp(ctx.thermalEnergy(), 0f, 1f);
        float exposureFactor = FogMath.lerp(0.5f, 1.0f, surface.exposure());

        float target = drynessFactor * heatFactor * exposureFactor;

        return new OverlayTargetResult(FogMath.clamp(target, 0f, 1f) * 10f, 1f);
    }
}