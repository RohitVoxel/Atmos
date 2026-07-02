package net.atmos.atmosphere.sky;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;

/**
 * Applies moon phase and storm darkening to the sky color.
 *
 * Moon phase modulates how much the night sky brightens — full moon = more
 * ambient light, new moon = maximum darkness. Storm suppression desaturates
 * and darkens the sky during thunderstorms.
 *
 * Luminance floor:
 * The sky can never go fully void-black regardless of how badly night +
 * storm stack. The floor is openness-scaled — open biomes (desert, ocean,
 * savanna) get a higher floor because a clear open sky at night should feel
 * vast and cold, not underground. Enclosed biomes (swamp, jungle) keep a
 * lower floor since their darkness is intentional and atmospheric.
 *
 * Openness is read directly from BiomeAtmosphereRegistry — the biome's
 * structural openness value, not a weather-derived proxy. Previous versions
 * used (1 - humidityMass) which collapsed the open-biome floor during storms:
 * a desert thunderstorm night incorrectly read as enclosed because elevated
 * humidityMass suppressed the luminance boost. Biome openness is static and
 * weather-independent — the correct source for a structural sky property.
 *
 * Moon tinting:
 * Uses proportional channel scaling so the blue-silver character of moonlight
 * registers correctly at any luminance level, including near-floor values.
 */
public final class MoonlightController {

    private static final float[] MOON_PHASE_STRENGTH = {
            1.0f, 0.75f, 0.50f, 0.25f, 0.10f, 0.25f, 0.50f, 0.75f
    };

    // Base luminance floor for enclosed biomes (openness = 0).
    // 0.060 ≈ RGB 15/255 — dark but sky structure is readable.
    private static final float MIN_SKY_LUMINANCE_BASE = 0.060f;

    // Additional luminance added at full openness (openness = 1.0).
    // Open biomes at night feel vast — stars are implied, horizon is visible.
    // 0.025 gives a floor of 0.085 for desert/ocean/savanna nights.
    private static final float MIN_SKY_LUMINANCE_OPEN = 0.025f;

    public int apply(int skyColor, FogContext ctx, EnvironmentalState env) {
        float nightDepth = env.getNightDepth();
        if (nightDepth <= 0f) return skyColor;

        int   phaseIdx     = (int) FogMath.clamp(ctx.level().getMoonPhase(), 0, 7);
        float moonStrength = MOON_PHASE_STRENGTH[phaseIdx] * nightDepth;

        float r = ((skyColor >> 16) & 0xFF) / 255f;
        float g = ((skyColor >>  8) & 0xFF) / 255f;
        float b = ( skyColor        & 0xFF) / 255f;

        // Moon phase tinting: proportional channel scaling.
        // Full moon: r scales to 0.930, g to 1.045, b to 1.120 of input.
        // New moon (moonStrength ~0.10): shifts are nearly zero — correct.
        if (moonStrength > 0f) {
            r *= FogMath.lerp(1.0f, 0.930f, moonStrength);
            g *= FogMath.lerp(1.0f, 1.045f, moonStrength);
            b *= FogMath.lerp(1.0f, 1.120f, moonStrength);
        }

        // Storm darkening: night thunderstorms should be very dark but not void.
        float stormEnergy = env.getStormEnergy();
        if (stormEnergy > 0f) {
            float gray     = r * 0.299f + g * 0.587f + b * 0.114f;
            float darkGray = gray * 0.55f;
            float ss       = stormEnergy * nightDepth;
            r = FogMath.lerp(r, darkGray, ss);
            g = FogMath.lerp(g, darkGray, ss);
            b = FogMath.lerp(b, darkGray, ss);
        }

        // --- Luminance floor ---
        // Openness read from BiomeAtmosphereRegistry — biome structural value,
        // not a weather proxy. humidityMass drifts with active weather and
        // would suppress the open-biome floor during storms, making a desert
        // thunderstorm night read as enclosed. Biome openness is weather-
        // independent and structurally correct for a sky luminance property.
        float openness   = BiomeAtmosphereRegistry.of(ctx.biome()).fog().openness();
        float floorBoost = openness * MIN_SKY_LUMINANCE_OPEN;
        float luminanceFloor = MIN_SKY_LUMINANCE_BASE + floorBoost;

        float luminance = r * 0.299f + g * 0.587f + b * 0.114f;

        if (luminance < luminanceFloor && luminance > 0f) {
            float scale = luminanceFloor / luminance;
            r = Math.min(1f, r * scale);
            g = Math.min(1f, g * scale);
            b = Math.min(1f, b * scale);
        } else if (luminance <= 0f) {
            r = 0.012f;
            g = 0.012f;
            b = luminanceFloor * 0.9f;
        }

        return packRGB(FogMath.clamp(r, 0f, 1f), FogMath.clamp(g, 0f, 1f), FogMath.clamp(b, 0f, 1f));
    }

    private static int packRGB(float r, float g, float b) {
        return ((int)(r * 255f) << 16) | ((int)(g * 255f) << 8) | (int)(b * 255f);
    }
}