package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.config.AtmosConfig;

/**
 * Night atmosphere: compression and enclosure, not just darkness.
 *
 * End compresses more than start — the visible band NARROWS.
 * Objects at mid-distance disappear first. The world feels enclosed.
 * Moon phase modulates both compression and color:
 *   - distance: full moon = less compression, new moon = maximum
 *   - color: full moon = silver-blue fog tint, new moon = no tint
 *
 * Moon color is storm-gated: heavy overcast suppresses the silver-blue
 * tint because the moon is not visible through cloud cover.
 *
 * Biome night identity:
 * After the base night shift, each biome's daytime color identity is
 * partially preserved into darkness. Swamp nights are warm and heavy —
 * the fog retains a trace of green-brown. Desert nights are cold and
 * exposed — the warmth bleeds out faster, leaving a pale cold air.
 * Ocean nights go deep blue. Jungle nights stay dense and dark-green.
 *
 * The push is toward the biome's raw fog color at reduced strength,
 * scaled by nightDepth and enclosure. Open biomes get less identity
 * at night — the open sky dominates. Enclosed biomes carry their
 * daytime character strongly into darkness.
 *
 * Openness scaling: compression is dampened in open biomes.
 * Enclosed biome floor and combined storm+night floor unchanged.
 *
 * Toggle: config.fog.nightCompression
 */
public final class NightFogModifier implements FogModifier {

    private static final float[] MOON_COMPRESSION_MOD = {
            0.82f, 0.87f, 0.92f, 0.97f, 1.00f, 0.97f, 0.92f, 0.87f
    };

    private static final float[] MOON_PHASE_STRENGTH = {
            1.0f, 0.75f, 0.50f, 0.25f, 0.10f, 0.25f, 0.50f, 0.75f
    };

    private static final float MOON_BLUE_LIFT  = 0.022f;
    private static final float MOON_RED_REDUCE = 0.014f;
    private static final float MOON_GREEN_LIFT = 0.006f;

    private static final float OPENNESS_REDUCTION          = 0.65f;
    private static final float ENCLOSED_THRESHOLD          = 0.30f;
    private static final float ENCLOSED_END_FLOOR          = 0.88f;
    private static final float STORM_NIGHT_STACK_THRESHOLD = 0.30f;
    private static final float STORM_NIGHT_FLOOR           = 0.68f;

    // Biome night identity: maximum push strength toward biome color.
    // 0.12 = subtle — the biome whispers its daytime identity into the dark.
    // Enclosed biomes get full strength; open biomes get a fraction.
    private static final float BIOME_IDENTITY_STRENGTH = 0.12f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        if (!AtmosConfig.get().fog.nightCompression) return fog;

        float nightDepth = env.nightDepth;
        if (nightDepth <= 0f) return fog;

        float strength = AtmosConfig.get().fog.safeNightFogStrength();
        if (strength <= 0f) return fog;

        float incomingEnd = fog.end();

        int   phaseIdx    = (int) FogMath.clamp(ctx.level().getMoonPhase(), 0, 7);
        float moonMod     = FogMath.lerp(1.0f, MOON_COMPRESSION_MOD[phaseIdx], nightDepth);
        float scaledNight = nightDepth * strength * moonMod;

        float opennessFactor      = FogMath.lerp(1.0f, OPENNESS_REDUCTION, fog.openness());
        float endCompression      = scaledNight * opennessFactor;

        float startOpennessFactor = FogMath.lerp(1.0f,
                FogMath.lerp(1.0f, OPENNESS_REDUCTION, 0.5f), fog.openness());
        float startCompression    = scaledNight * startOpennessFactor;

        float end   = fog.end()   * FogMath.lerp(1.0f, 0.80f, endCompression);
        float start = fog.start() * FogMath.lerp(1.0f, 0.87f, startCompression);

        // --- Enclosed biome floor ---
        if (fog.openness() < ENCLOSED_THRESHOLD && nightDepth > 0.6f) {
            float enclosedFloor = incomingEnd * ENCLOSED_END_FLOOR;
            end = Math.max(end, enclosedFloor);
        }

        // --- Combined storm+night floor ---
        float stormNightProduct = env.stormEnergy * nightDepth;
        if (stormNightProduct > STORM_NIGHT_STACK_THRESHOLD) {
            float stackFloor = incomingEnd * STORM_NIGHT_FLOOR;
            end = Math.max(end, stackFloor);
        }

        // --- Base night color shift ---
        float dewFactor = env.humidityMass * nightDepth * 0.08f;
        start = FogMath.clamp(start * (1f - dewFactor), 1f, end * 0.75f);

        float blue  = Math.min(1f, fog.blue()  + 0.040f * scaledNight);
        float red   = Math.max(0f, fog.red()   - 0.022f * scaledNight);
        float green = Math.max(0f, fog.green() - 0.008f * scaledNight);

        float nightGray = (red * 0.299f + green * 0.587f + blue * 0.114f) * 0.92f;
        float softening = scaledNight * 0.18f;
        red   = FogMath.lerp(red,   nightGray + 0.01f, softening);
        green = FogMath.lerp(green, nightGray,          softening);
        blue  = FogMath.lerp(blue,  nightGray + 0.03f, softening);

        // --- Biome night identity ---
        // Each biome retains a trace of its daytime color character at night.
        // The push is toward the biome's raw fog color at BIOME_IDENTITY_STRENGTH.
        // Enclosed biomes (swamp, jungle) carry full identity — their darkness
        // is defined by their character. Open biomes (desert, ocean) get a
        // reduced push — the open sky dilutes biome identity at night.
        //
        // Storm suppressed: heavy overcast night is uniform grey regardless of biome.
        // Identity strength scales with nightDepth — the push is strongest at midnight.
        BiomeTraits biome         = BiomeAtmosphereRegistry.of(ctx.biome()).fog();
        float enclosureScale      = FogMath.lerp(0.25f, 1.0f, 1f - fog.openness());
        float stormSuppressIdent  = FogMath.clamp(1f - env.stormEnergy * 1.8f, 0f, 1f);
        float identityPush        = BIOME_IDENTITY_STRENGTH
                * nightDepth * enclosureScale * stormSuppressIdent;

        if (identityPush > 0.001f) {
            // Push current night color toward biome's raw identity.
            // The direction is always from current toward biome color —
            // warm biomes pull red up, cool biomes pull blue up.
            red   = FogMath.lerp(red,   biome.red(),   identityPush);
            green = FogMath.lerp(green, biome.green(), identityPush);
            blue  = FogMath.lerp(blue,  biome.blue(),  identityPush);

            // Darken the identity push proportionally — biome color at night
            // is a dark version of the daytime color, not the full daylight hue.
            // 0.55 multiplier ensures the push reads as nocturnal, not daytime.
            float nightDarken = 0.55f + 0.45f * (1f - nightDepth);
            red   *= nightDarken;
            green *= nightDarken;
            blue  *= nightDarken;
        }

        // --- Moon phase color response (storm-gated) ---
        float stormSuppression = FogMath.smoothstep(
                FogMath.clamp(1f - env.stormEnergy * 1.4f, 0f, 1f));
        float moonColorStr = MOON_PHASE_STRENGTH[phaseIdx] * nightDepth * stormSuppression;

        red   = Math.max(0f, red   - MOON_RED_REDUCE * moonColorStr);
        green = Math.min(1f, green + MOON_GREEN_LIFT  * moonColorStr);
        blue  = Math.min(1f, blue  + MOON_BLUE_LIFT   * moonColorStr);

        return fog.with(start, end,
                FogMath.clamp(red, 0f, 1f),
                FogMath.clamp(green, 0f, 1f),
                FogMath.clamp(blue, 0f, 1f));
    }
}