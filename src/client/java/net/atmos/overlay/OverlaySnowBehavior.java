package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Snow target evaluation — Batch 2 Phase 2.
 *
 * Reuses OverlayManager's already-published SNOW contribution (Season +
 * Rain publishers, Phase 1) as the global winter tendency, then modulates
 * it per-surface using the cached biome temperature, this surface's world
 * altitude, sky exposure, and the current rain level. Season progression
 * itself is never recomputed here — that remains OverlaySeasonalPublisher's
 * responsibility.
 */
public final class OverlaySnowBehavior {

    private OverlaySnowBehavior() {}

    private static final float ALTITUDE_COOLING_RANGE = 300f;
    private static final float ALTITUDE_COOLING_MAX   = 0.35f;
    private static final float SNOW_TEMP_THRESHOLD    = 0.30f;
    private static final float SNOW_TEMP_BAND         = 0.55f;
    private static final float SEA_LEVEL              = 64f;

    public static OverlayTargetResult evaluate(OverlaySurface surface, float globalSnowContribution,
                                               OverlayEnvironmentalContext ctx) {
        float altitudeCooling = FogMath.clamp((surface.pos().getY() - SEA_LEVEL) / ALTITUDE_COOLING_RANGE, 0f, 1f)
                * ALTITUDE_COOLING_MAX;

        float effectiveTemp = surface.temperature() - altitudeCooling
                - ctx.seasonal().influence().temperatureInfluence() * 0.30f
                - (ctx.thermalEnergy() - 0.5f) * 0.15f;

        float coldFactor = FogMath.smoothstep(
                FogMath.clamp((SNOW_TEMP_THRESHOLD - effectiveTemp) / SNOW_TEMP_BAND, 0f, 1f));

        if (coldFactor < 0.02f) {
            return new OverlayTargetResult(0f, 1f);
        }

        float snowfallBoost   = ctx.rainLevel() * coldFactor;
        float exposureFactor  = FogMath.lerp(0.55f, 1.0f, surface.exposure());
        float seasonalTendency = FogMath.lerp(0.35f, 1.0f, globalSnowContribution);

        float target = coldFactor * exposureFactor * seasonalTendency * (0.4f + 0.6f * snowfallBoost);

        float seasonStrength = ctx.seasonal().calendar().seasonStrength();
        float rateMultiplier = FogMath.lerp(0.6f, 1.4f, seasonStrength);

        return new OverlayTargetResult(FogMath.clamp(target, 0f, 1f) * 10f, rateMultiplier);
    }
}