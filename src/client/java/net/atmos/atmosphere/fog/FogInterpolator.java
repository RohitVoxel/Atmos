package net.atmos.atmosphere.fog;

import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.config.AtmosConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Biome-to-biome fog interpolation with velocity-adaptive commit timing.
 *
 * Hold time scales with player movement speed so Elytra/horse travel doesn't
 * freeze the atmosphere waiting for a hold that never completes.
 *
 * Speed is read from FogContext.getSmoothedSpeed() — the single authoritative
 * estimate shared by both the biome cache threshold and the hold time.
 *
 * Hysteresis:
 * When the candidate biome's fog end is within HYSTERESIS_THRESHOLD of the
 * current fog end, AND the biome's raw fog color is within HYSTERESIS_COLOR_THRESHOLD
 * of the current blend target color, the biome is silently accepted without
 * restarting the blend. Both conditions must hold — distance similarity alone
 * is insufficient. Forest→dark_forest (8-block end delta, 0.13 color delta)
 * previously silent-accepted with stale forest colors locked in. With the color
 * check, it falls through to the normal hold+blend path and transitions correctly.
 */
public final class FogInterpolator {

    private static final float HOLD_TIME_WALK  = 2.2f;
    private static final float HOLD_TIME_FAST  = 0.5f;
    private static final float SPEED_WALK      = 4.5f;
    private static final float SPEED_FAST      = 18.0f;

    // Distance threshold for hysteresis silent-accept.
    private static final float HYSTERESIS_THRESHOLD = 12.0f;

    // Color threshold for hysteresis silent-accept.
    // Sum of absolute differences across R, G, B channels.
    // 0.08 = ~20/255 per channel average — visually similar fog profiles.
    // Forest (0.60/0.67/0.61) vs dark_forest (0.55/0.62/0.58) = 0.13 → blend.
    // Ocean vs deep_ocean (same profile) = 0.00 → silent accept.
    // Two DEFAULT biomes = 0.00 → silent accept.
    private static final float HYSTERESIS_COLOR_THRESHOLD = 0.08f;

    private static final float EXPANSION_SPEED_MULT = 0.32f;
    private static final float FAST_EXPANSION_SPEED = 15.0f;

    private float fromStart, fromEnd, fromRed, fromGreen, fromBlue;
    private float fromOpenness, fromContrastRetention, fromWeatherSensitivity, fromHumidity;

    private float toStart, toEnd, toRed, toGreen, toBlue;
    private float toOpenness, toContrastRetention, toWeatherSensitivity, toHumidity;

    private float blendProgress  = 1.0f;
    private float blendSpeedMult = 1.0f;

    private Holder<Biome> activeBiome    = null;
    private Holder<Biome> pendingBiome   = null;
    private float         pendingHeldFor = 0f;

    private float currentOpenness           = 0.5f;
    private float currentContrastRetention  = 0.5f;
    private float currentWeatherSensitivity = 0.6f;
    private float currentHumidity           = 0.35f;

    public void advance(FogContext ctx, FogState lastFog, float deltaSec) {
        float speed = FogContext.getSmoothedSpeed();

        Holder<Biome> detected = ctx.biome();

        if (detected.equals(pendingBiome)) {
            pendingHeldFor += deltaSec;
        } else {
            pendingBiome   = detected;
            pendingHeldFor = 0f;
        }

        float   holdTime   = adaptiveHoldTime(speed);
        boolean firstFrame = (activeBiome == null);
        boolean stable     = pendingHeldFor >= holdTime;
        boolean different  = !pendingBiome.equals(activeBiome);

        if (!firstFrame && !(stable && different)) {
            advanceBlend(deltaSec);
            return;
        }

        if (!firstFrame) {
            BiomeTraits candidateTraits = BiomeAtmosphereRegistry.of(pendingBiome).fog();

            boolean distSimilar  = Math.abs(candidateTraits.end() - lastFog.end()) < HYSTERESIS_THRESHOLD;

            // Color similarity: compare candidate's raw biome color to the current
            // blend target (toRed/Green/Blue), not to lastFog which is pipeline-modified.
            // Pipeline modifiers (daylight, weather, night) apply uniformly to all
            // biomes — comparing raw-to-raw isolates the actual biome identity delta.
            float colorDiff = Math.abs(candidateTraits.red()   - toRed)
                    + Math.abs(candidateTraits.green() - toGreen)
                    + Math.abs(candidateTraits.blue()  - toBlue);
            boolean colorSimilar = colorDiff < HYSTERESIS_COLOR_THRESHOLD;

            if (distSimilar && colorSimilar) {
                activeBiome    = pendingBiome;
                pendingHeldFor = 0f;
                advanceBlend(deltaSec);
                return;
            }
        }

        fromStart              = lastFog.start();
        fromEnd                = lastFog.end();
        fromRed                = lastFog.red();
        fromGreen              = lastFog.green();
        fromBlue               = lastFog.blue();
        fromOpenness           = currentOpenness;
        fromContrastRetention  = currentContrastRetention;
        fromWeatherSensitivity = currentWeatherSensitivity;
        fromHumidity           = currentHumidity;

        BiomeTraits traits = BiomeAtmosphereRegistry.of(pendingBiome).fog();

        toStart              = traits.start();
        toEnd                = traits.end();
        toRed                = traits.red();
        toGreen              = traits.green();
        toBlue               = traits.blue();
        toOpenness           = traits.openness();
        toContrastRetention  = traits.contrastRetention();
        toWeatherSensitivity = traits.weatherSensitivity();
        toHumidity           = traits.humidity();

        if (toEnd > fromEnd) {
            float speedFactor = FogMath.clamp((speed - SPEED_WALK) / (FAST_EXPANSION_SPEED - SPEED_WALK), 0f, 1f);
            blendSpeedMult = FogMath.lerp(EXPANSION_SPEED_MULT, 1.0f, speedFactor);
        } else {
            blendSpeedMult = 1.0f;
        }

        boolean blending = AtmosConfig.get().fog.biomeFogBlending;
        blendProgress = (firstFrame || !blending) ? 1.0f : 0.0f;

        activeBiome    = pendingBiome;
        pendingHeldFor = 0f;
    }

    public void reset() {
        activeBiome    = null;
        pendingBiome   = null;
        pendingHeldFor = 0f;
        blendProgress  = 1.0f;
        blendSpeedMult = 1.0f;

        currentOpenness           = 0.5f;
        currentContrastRetention  = 0.5f;
        currentWeatherSensitivity = 0.6f;
        currentHumidity           = 0.35f;

        fromStart = toStart = 8f;
        fromEnd   = toEnd   = 96f;
        fromRed   = toRed   = 0.72f;
        fromGreen = toGreen = 0.78f;
        fromBlue  = toBlue  = 0.84f;
        fromOpenness           = toOpenness           = 0.5f;
        fromContrastRetention  = toContrastRetention  = 0.5f;
        fromWeatherSensitivity = toWeatherSensitivity = 0.6f;
        fromHumidity           = toHumidity           = 0.35f;
    }

    private float adaptiveHoldTime(float speed) {
        float t = FogMath.clamp((speed - SPEED_WALK) / (SPEED_FAST - SPEED_WALK), 0f, 1f);
        return FogMath.lerp(HOLD_TIME_WALK, HOLD_TIME_FAST, t);
    }

    private void advanceBlend(float deltaSec) {
        if (blendProgress < 1.0f) {
            float duration = AtmosConfig.get().fog.blendDuration();
            blendProgress  = Math.min(1.0f, blendProgress + (deltaSec / duration) * blendSpeedMult);
        }
    }

    public FogState resolve() {
        float t;
        if (toEnd > fromEnd) {
            t = 1.0f - (float) Math.pow(1.0f - blendProgress, 3.2f);
        } else {
            t = FogMath.smoothstep(blendProgress);
        }

        currentOpenness           = FogMath.lerp(fromOpenness,           toOpenness,           t);
        currentContrastRetention  = FogMath.lerp(fromContrastRetention,  toContrastRetention,  t);
        currentWeatherSensitivity = FogMath.lerp(fromWeatherSensitivity, toWeatherSensitivity, t);
        currentHumidity           = FogMath.lerp(fromHumidity,           toHumidity,           t);

        return new FogState(
                FogMath.lerp(fromStart, toStart, t),
                FogMath.lerp(fromEnd,   toEnd,   t),
                FogMath.lerp(fromRed,   toRed,   t),
                FogMath.lerp(fromGreen, toGreen, t),
                FogMath.lerp(fromBlue,  toBlue,  t),
                currentOpenness,
                currentContrastRetention,
                currentWeatherSensitivity,
                currentHumidity
        );
    }
}