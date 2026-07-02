package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;
import net.atmos.atmosphere.fog.biome.BiomeTraits;

/**
 * Applies an exponential density curve to fog distances, then amplifies
 * the biome's natural color identity using contrast retention.
 *
 * The density curve makes fog feel physically volumetric: it builds slowly
 * at the near edge and accelerates toward the far edge, matching how real
 * atmospheric scattering accumulates over distance.
 *
 * Contrast retention: pushes fog color away from its own neutral to deepen
 * biome identity in hazy conditions.
 *
 * Neutral reference:
 * Previous implementation derived the neutral from fog.red/green/blue at
 * pipeline position 4 — after HeightFogModifier (altitude color shifts) and
 * AtmosphericLayerModifier (mist brightness lifts) have already run. This
 * caused contrast retention to amplify upstream modifier artifacts rather
 * than the biome's own color character:
 *   - Mist lifts blue → neutral shifts blue-ward → retention amplifies blue
 *   - Altitude shifts red/blue → neutral drifts → retention magnifies altitude
 *
 * Now the neutral is computed from the biome's raw fog color retrieved from
 * BiomeAtmosphereRegistry. Pipeline modifiers apply uniformly to all biomes
 * and shouldn't influence what contrast retention considers "neutral" for that
 * biome. The push always goes in the direction of the biome's actual identity:
 *   - Warm biomes (desert, badlands): red/yellow amplification
 *   - Cool biomes (ocean, taiga): blue/grey amplification
 *   - Neutral biomes (default, forest): subtle enhancement only
 *
 * Output channels (fog.red/green/blue) remain the pipeline-accumulated values
 * — only the neutral reference point changes.
 */
public final class DepthFogModifier implements FogModifier {

    private static final float DENSITY_RATE_MAX = 2.2f;
    private static final float DENSITY_RATE_MIN = 0.7f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        float end = fog.end();
        if (end <= 0f) return fog;

        float densityRate = FogMath.lerp(DENSITY_RATE_MAX, DENSITY_RATE_MIN, fog.openness());
        float normStart   = FogMath.clamp(fog.start() / end, 0f, 1f);
        float curvedStart = densityCurve(normStart, densityRate);
        float curvedEnd   = densityCurve(1.0f,      densityRate);

        float scale    = (curvedEnd > 0f) ? 1.0f / curvedEnd : 1.0f;
        float newStart = end * curvedStart * scale;
        float newEnd   = end * curvedEnd   * scale;

        float daylightClear = Math.max(0f, (float) Math.cos(ctx.sunAngle())) * fog.openness() * 0.14f;
        newEnd   *= (1.0f + daylightClear);
        newStart *= (1.0f + daylightClear * 0.6f);

        // Contrast retention: amplify the biome's own color character.
        //
        // Neutral is derived from the biome's raw fog profile color, not from
        // the current pipeline fog state. By position 4, HeightFogModifier and
        // AtmosphericLayerModifier have already shifted the fog channels —
        // using fog.red/green/blue for the neutral would cause retention to
        // amplify those upstream shifts rather than the biome's identity.
        //
        // BiomeAtmosphereRegistry lookup is cheap (tag checks, already cached
        // by FogContext.dominantBiomeCached) and gives the stable biome color
        // that contrast retention is designed to express.
        BiomeTraits biomeTraits = BiomeAtmosphereRegistry.of(ctx.biome()).fog();
        float biomeNeutral = biomeTraits.red()   * 0.299f
                + biomeTraits.green() * 0.587f
                + biomeTraits.blue()  * 0.114f;

        float hazeFactor     = FogMath.clamp(1.0f - normStart, 0f, 1f)
                * FogMath.lerp(0.15f, 0.55f, 1f - fog.openness());
        float retainStrength = fog.contrastRetention() * hazeFactor;

        // Push pipeline-accumulated channels away from biome neutral.
        // The direction (warm vs cool) is determined by how each channel
        // of the current fog compares to the biome's perceptual neutral.
        float red   = FogMath.clamp(fog.red()   + (fog.red()   - biomeNeutral) * 0.12f * retainStrength, 0f, 1f);
        float green = FogMath.clamp(fog.green() + (fog.green() - biomeNeutral) * 0.10f * retainStrength, 0f, 1f);
        float blue  = FogMath.clamp(fog.blue()  + (fog.blue()  - biomeNeutral) * 0.14f * retainStrength, 0f, 1f);

        return fog.with(Math.max(2f, newStart), Math.max(newStart + 1f, newEnd), red, green, blue);
    }

    private static float densityCurve(float x, float rate) {
        return 1f - (float) Math.exp(-x * rate);
    }
}