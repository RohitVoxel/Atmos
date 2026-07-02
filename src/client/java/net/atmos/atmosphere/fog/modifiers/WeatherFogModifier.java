package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.config.AtmosConfig;

/**
 * Weather effects on fog: storm compression, rain brightening, thunder cold shift,
 * post-rain atmosphere, pre-storm approach, post-storm clearing, and freezing rain.
 *
 * Freezing rain:
 * In cold biomes (base temperature < 0.20), precipitation is ice crystals
 * rather than liquid water. Ice crystals scatter light upward — the fog
 * becomes bright and pale (steel-white) rather than dark and heavy.
 * The grey darkening applied in warm rain is replaced by a brightness lift
 * and steel-white tint. Fog compression is unchanged — cold storms are
 * still stormy, just atmospherically different.
 *
 * The temperature blends across 0.15–0.25 so the transition from cold to
 * temperate rain is gradual rather than a hard cutoff at the border.
 *
 * Toggle: config.fog.weatherEffects
 */
public final class WeatherFogModifier implements FogModifier {

    private static final float HEAVY_STORM_THRESHOLD   = 0.45f;
    private static final float HEAVY_RAIN_BRIGHTEN     = 0.05f;
    private static final float THUNDER_COLD_SHIFT      = 0.032f;
    private static final float OPENNESS_REDUCTION      = 0.70f;

    private static final float POSTRAIN_HUMIDITY_MIN   = 0.45f;
    private static final float POSTRAIN_STORM_GATE     = 5.0f;
    private static final float POSTRAIN_RAIN_GATE      = 6.0f;
    private static final float POSTRAIN_SIGNAL_MIN     = 0.02f;

    private static final float APPROACH_STORM_SUPPRESS = 0.35f;

    private static final float CLEARING_STORM_GATE     = 0.25f;
    private static final float CLEARING_SIGNAL_MIN     = 0.03f;

    // Freezing rain temperature band.
    // Below COLD_THRESHOLD: fully cold rain response (steel-white).
    // Above COLD_BLEND_MAX: fully warm rain response (grey-dark).
    // Between: blended transition.
    private static final float COLD_THRESHOLD  = 0.15f;
    private static final float COLD_BLEND_MAX  = 0.25f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        if (!AtmosConfig.get().fog.weatherEffects) return fog;

        float stormEnergy   = env.stormEnergy;
        float stormApproach = env.stormApproach;
        float stormClearing = env.stormClearing;
        float thunder       = ctx.thunder();

        // --- Post-rain signal ---
        float humidityResidual = FogMath.clamp(
                (env.humidityMass - POSTRAIN_HUMIDITY_MIN) / (1f - POSTRAIN_HUMIDITY_MIN), 0f, 1f);
        float stormAbsent = FogMath.clamp(1f - stormEnergy * POSTRAIN_STORM_GATE, 0f, 1f);
        float rainAbsent  = FogMath.clamp(1f - ctx.rain()   * POSTRAIN_RAIN_GATE, 0f, 1f);
        float postRain    = humidityResidual * stormAbsent * rainAbsent;

        boolean hasStormActivity = stormEnergy > 0f || thunder > 0f;
        boolean hasPostRain      = postRain > POSTRAIN_SIGNAL_MIN;
        boolean hasApproach      = stormApproach > 0.02f && stormEnergy < APPROACH_STORM_SUPPRESS;
        boolean hasClearing      = stormClearing > CLEARING_SIGNAL_MIN
                && stormEnergy < CLEARING_STORM_GATE;

        if (!hasStormActivity && !hasPostRain && !hasApproach && !hasClearing) return fog;

        float intensity = AtmosConfig.get().fog.safeWeatherIntensity();
        if (intensity <= 0f) return fog;

        // Freezing rain factor: 1.0 = fully cold, 0.0 = fully warm.
        // Blends across COLD_THRESHOLD → COLD_BLEND_MAX temperature band.
        float biomeTemp  = ctx.biome().value().getBaseTemperature();
        float coldFactor = FogMath.clamp(
                1f - (biomeTemp - COLD_THRESHOLD) / (COLD_BLEND_MAX - COLD_THRESHOLD),
                0f, 1f);

        float start = fog.start();
        float end   = fog.end();
        float red   = fog.red();
        float green = fog.green();
        float blue  = fog.blue();

        // --- Pre-storm approach ---
        if (hasApproach) {
            float suppressFade = FogMath.clamp(
                    1f - stormEnergy / APPROACH_STORM_SUPPRESS, 0f, 1f);
            float ap = FogMath.smoothstep(stormApproach) * suppressFade * intensity;

            start *= FogMath.lerp(1.0f, 0.92f, ap * fog.weatherSensitivity());
            red   -= 0.012f * ap * fog.weatherSensitivity();
            blue  -= 0.008f * ap * fog.weatherSensitivity();
            green -= 0.004f * ap * fog.weatherSensitivity();
            end   *= FogMath.lerp(1.0f, 0.97f, ap);
        }

        // --- Active storm: compression and color shift ---
        if (hasStormActivity) {
            float opennessFactor = FogMath.lerp(1.0f, OPENNESS_REDUCTION, fog.openness());
            float scaledStorm    = stormEnergy * intensity * opennessFactor;
            float scaledThunder  = thunder     * intensity * fog.weatherSensitivity();

            end   *= FogMath.lerp(1.0f, 0.80f, scaledStorm)
                    * FogMath.lerp(1.0f, 0.88f, scaledThunder);
            start *= FogMath.lerp(1.0f, 0.87f, scaledStorm);

            if (stormEnergy > 0f) {
                // Warm rain: grey darkening + blue lift.
                // Cold rain: brightness lift + steel-white tint.
                // Blend between the two based on biome temperature.
                float warmGrayShift = FogMath.lerp(0.035f, 0.008f, fog.openness())
                        * stormEnergy * intensity;
                float warmBlueShift = FogMath.lerp(0.02f, 0.05f, fog.openness())
                        * stormEnergy * intensity;

                // Cold rain response: steel-white brightness lift.
                // Ice crystals scatter light — the fog brightens and whitens.
                float coldBrighten = stormEnergy * intensity * 0.045f * coldFactor;
                float coldWhiten   = stormEnergy * intensity * 0.030f * coldFactor;

                // Warm response (inverted by coldFactor).
                float warmFactor = 1f - coldFactor;
                red   -= warmGrayShift  * warmFactor;
                green -= warmGrayShift  * warmFactor;
                blue  += warmBlueShift  * warmFactor;

                // Cold response.
                red   += coldBrighten;
                green += coldBrighten;
                blue  += coldBrighten + coldWhiten;

                if (stormEnergy > HEAVY_STORM_THRESHOLD) {
                    float heavyFactor = (stormEnergy - HEAVY_STORM_THRESHOLD)
                            / (1f - HEAVY_STORM_THRESHOLD);
                    float brighten = heavyFactor * HEAVY_RAIN_BRIGHTEN;
                    red   += brighten;
                    green += brighten;
                    blue  += brighten * 0.4f;
                }
            }

            if (thunder > 0f) {
                float gray     = red * 0.299f + green * 0.587f + blue * 0.114f;
                float coldGray = gray * 0.87f;
                red   = FogMath.lerp(red,   coldGray,                      scaledThunder * 0.4f);
                green = FogMath.lerp(green, coldGray,                      scaledThunder * 0.4f);
                blue  = FogMath.lerp(blue,  coldGray + THUNDER_COLD_SHIFT, scaledThunder * 0.5f);
            }
        }

        // --- Thunder flash: fog bleach ---
        // When lightning strikes, fog briefly bleaches toward white-bright
        // before returning to storm grey. Uses the same thunderFlash signal
        // as SkyColorController so sky and fog respond simultaneously.
        float thunderFlash = env.getThunderFlash();
        if (thunderFlash > 0.001f && hasStormActivity) {
            float flashStr = FogMath.smoothstep(thunderFlash) * intensity;
            // Bleach fog color toward white — lightning illuminates all
            // atmospheric particles simultaneously.
            float flashGray = red * 0.299f + green * 0.587f + blue * 0.114f;
            float white     = Math.min(1f, flashGray + 0.25f * flashStr);
            red   = FogMath.lerp(red,   white, flashStr * 0.6f);
            green = FogMath.lerp(green, white, flashStr * 0.6f);
            blue  = FogMath.lerp(blue,  white, flashStr * 0.5f);

            // Briefly expand fog start — the world is illuminated.
            start *= FogMath.lerp(1.0f, 1.18f, flashStr);
            start  = Math.min(start, end * 0.85f);
        }
        
        // --- Post-storm clearing ---
        if (hasClearing) {
            float cl = FogMath.smoothstep(FogMath.clamp(
                    (stormClearing - CLEARING_SIGNAL_MIN) / (1f - CLEARING_SIGNAL_MIN), 0f, 1f));
            float openScale = FogMath.lerp(0.4f, 1.0f, fog.openness());
            float clScaled  = cl * openScale * intensity;

            end   *= (1f + 0.12f * clScaled);
            start  = Math.min(start, end * 0.80f);

            red   -= 0.015f * clScaled;
            green += 0.005f * clScaled;
            blue  += 0.025f * clScaled;
        }

        // --- Post-rain atmosphere ---
        if (hasPostRain) {
            float pr = FogMath.smoothstep(FogMath.clamp(
                    (postRain - POSTRAIN_SIGNAL_MIN) / (1f - POSTRAIN_SIGNAL_MIN), 0f, 1f));

            red   -= 0.018f * pr;
            green -= 0.005f * pr;
            blue  += 0.020f * pr;

            if (!hasStormActivity) {
                end   *= (1f + 0.06f * pr);
                start  = Math.min(start, end * 0.82f);
            }
        }

        return fog.with(start, end,
                FogMath.clamp(red, 0f, 1f), FogMath.clamp(green, 0f, 1f), FogMath.clamp(blue, 0f, 1f));
    }
}