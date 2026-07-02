package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;

/**
 * Adjusts fog based on time of day: dawn warmth, noon clarity, dusk orange.
 *
 * horizonMask uses Math.abs(sunHeight) — zero at noon AND at midnight,
 * peaks at the horizon. Prevents dusk/dawn tints from amplifying during
 * the night where sunHeight is negative.
 *
 * Winter sun color:
 * Cold biomes at noon show a warm golden-amber quality — light traveling
 * through more atmosphere at the low polar sun angle.
 *
 * Snow field light:
 * In open cold biomes, snow reflects light back upward — the atmosphere
 * brightens to a white-luminous quality. The fog near-plane brightens,
 * the horizon glows, and the scene feels lighter than it physically is
 * because of the upward reflected light from the snow surface below.
 *
 * The effect scales with openness — an open snowy plain reflects far more
 * light than a snowy taiga with dense canopy intercepting it. It scales
 * with dayFactor — reflected light requires sunlight to reflect. Overcast
 * days still show a reduced version because cloud light still scatters off
 * snow, but stormEnergy suppresses it at high storm levels.
 */
public final class DaylightFogModifier implements FogModifier {

    private static final float COLD_THERMAL_THRESHOLD = 0.40f;

    // Winter sun color magnitudes.
    private static final float WINTER_RED_LIFT   = 0.030f;
    private static final float WINTER_GREEN_LIFT = 0.014f;
    private static final float WINTER_BLUE_DROP  = 0.022f;

    // Snow field light magnitudes.
    // White-bright lift: all channels raise, blue most — cold reflected light
    // has a blue-white quality distinct from the amber of winter sun.
    // Distance expansion: reflected light makes atmosphere feel thinner.
    private static final float SNOW_RED_LIFT      = 0.028f;
    private static final float SNOW_GREEN_LIFT    = 0.032f;
    private static final float SNOW_BLUE_LIFT     = 0.042f;
    private static final float SNOW_DIST_EXPAND   = 0.08f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        float angle       = ctx.sunAngle();
        float sunHeight   = (float) Math.cos(angle);
        float dayFactor   = Math.max(0f, sunHeight);
        float noonFactor  = dayFactor * dayFactor;
        float sinAngle    = (float) Math.sin(angle);
        float horizonMask = FogMath.clamp(1f - Math.abs(sunHeight) * 3f, 0f, 1f);
        float dawnFactor  = Math.max(0f,  sinAngle) * horizonMask;
        float duskFactor  = Math.max(0f, -sinAngle) * horizonMask;

        float start = fog.start(), end = fog.end();
        float red = fog.red(), green = fog.green(), blue = fog.blue();

        end   *= FogMath.lerp(0.90f, 1.08f, dayFactor);
        start *= FogMath.lerp(0.92f, 1.05f, dayFactor);

        float openSunBoost = dayFactor * fog.openness() * 0.13f;
        end   *= (1.0f + openSunBoost);
        start *= (1.0f + openSunBoost * 0.5f);

        float thermalClear = env.thermalEnergy * noonFactor * 0.14f;
        end   *= (1.0f + thermalClear);
        start *= (1.0f + thermalClear * 1.4f);

        // --- Noon color shift ---
        float noonStrength = noonFactor * FogMath.lerp(1.0f, 0.4f, fog.openness());
        red   += 0.015f * noonStrength;
        green += 0.008f * noonStrength;
        blue  -= 0.025f * noonStrength;

        if (env.thermalEnergy < COLD_THERMAL_THRESHOLD && dayFactor > 0f) {
            float coldness = FogMath.clamp(
                    1f - env.thermalEnergy / COLD_THERMAL_THRESHOLD, 0f, 1f);

            // --- Winter sun color (noon peak) ---
            float winterStrength = FogMath.smoothstep(coldness) * noonFactor;
            if (winterStrength > 0.001f) {
                red   = Math.min(1f, red   + WINTER_RED_LIFT   * winterStrength);
                green = Math.min(1f, green + WINTER_GREEN_LIFT * winterStrength);
                blue  = Math.max(0f, blue  - WINTER_BLUE_DROP  * winterStrength);
            }

            // --- Snow field light (all daylight hours, openness-scaled) ---
            // Reflected upwelling light from snow surface.
            // Scales with openness — dense taiga canopy intercepts most reflection.
            // Scales with dayFactor (not noonFactor²) — present all day, not just noon.
            // Storm partially suppresses: overcast diffuses but doesn't eliminate it.
            float stormDim      = FogMath.clamp(1f - env.stormEnergy * 0.7f, 0f, 1f);
            float snowStrength  = FogMath.smoothstep(coldness)
                    * dayFactor * fog.openness() * stormDim;

            if (snowStrength > 0.001f) {
                // White-bright lift — all channels raise, blue most.
                red   = Math.min(1f, red   + SNOW_RED_LIFT   * snowStrength);
                green = Math.min(1f, green + SNOW_GREEN_LIFT * snowStrength);
                blue  = Math.min(1f, blue  + SNOW_BLUE_LIFT  * snowStrength);

                // Distance expansion: reflected light thins apparent atmosphere.
                // Only expand end, proportionally adjust start to preserve ratio.
                float expandedEnd = end * (1f + SNOW_DIST_EXPAND * snowStrength);
                float ratio       = (end > 0f) ? start / end : 0.3f;
                end   = expandedEnd;
                start = Math.min(end * ratio, end * 0.85f);
            }
        }

        // --- Dawn ---
        float dawnDensity = dawnFactor * FogMath.lerp(0.10f, 0.06f, fog.openness());
        end   *= (1.0f - dawnDensity);
        start *= (1.0f - dawnDensity * 1.8f);
        red    = Math.min(1f, red   + 0.06f  * dawnFactor);
        green  = Math.min(1f, green + 0.025f * dawnFactor);
        blue   = Math.min(1f, blue  + 0.010f * dawnFactor);

        // --- Dusk ---
        end  *= (1.0f - duskFactor * 0.13f);
        red   = Math.min(1f, red   + 0.05f  * duskFactor);
        green = Math.min(1f, green + 0.015f * duskFactor);
        blue += 0.03f * (1f - dayFactor);

        return fog.with(start, end,
                FogMath.clamp(red, 0f, 1f),
                FogMath.clamp(green, 0f, 1f),
                FogMath.clamp(blue, 0f, 1f));
    }
}