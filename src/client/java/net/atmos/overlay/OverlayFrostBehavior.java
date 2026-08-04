package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Frost target evaluation — Batch 2 Phase 2.
 *
 * Frost has no seasonal publisher (unlike Snow/Pollen), so this evaluator
 * reads directly from the cached surface's biome temperature/humidity and
 * the current tick's night depth and rain level.
 */
public final class OverlayFrostBehavior {

    private OverlayFrostBehavior() {}

    private static final float FROST_TEMP_CENTER = 0.15f;
    private static final float FROST_TEMP_BAND   = 0.35f;

    public static OverlayTargetResult evaluate(OverlaySurface surface, OverlayEnvironmentalContext ctx) {
        float coldFactor = FogMath.clamp(
                1f - Math.abs(surface.temperature() - FROST_TEMP_CENTER) / FROST_TEMP_BAND, 0f, 1f);

        float humidityFactor  = FogMath.clamp((surface.humidity() + ctx.humidityMass()) * 0.5f, 0f, 1f);
        float clearSkyFactor  = FogMath.clamp(1f - ctx.rainLevel() * 2.5f, 0f, 1f);
        float nightFactor     = ctx.nightDepth();
        float exposureFactor  = FogMath.lerp(0.6f, 1.0f, surface.exposure());
        float warmthDissolve  = FogMath.clamp((ctx.thermalEnergy() - 0.5f) * 2f, 0f, 1f);

        float target = coldFactor * humidityFactor * clearSkyFactor * nightFactor
                * exposureFactor * (1f - warmthDissolve);

        return new OverlayTargetResult(FogMath.clamp(target, 0f, 1f) * 10f, 1f);
    }
}