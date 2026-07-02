package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.config.AtmosConfig;
import net.minecraft.core.BlockPos;

/**
 * Differentiates atmosphere based on sky exposure.
 *
 * Two distinct readings of the same rain event:
 *
 * EXPOSED (canSeeSky = true, rain > 0):
 *   The player is getting rained on directly. Near-fog density increases
 *   slightly — the air immediately around the player is wetter and heavier.
 *   A small near-fog start pull creates the "you are getting wet" sense.
 *
 * SHELTERED (canSeeSky = false, humidity > 0):
 *   Under a canopy, the player is cut off from direct rain. The atmosphere
 *   is enclosed and dripping — more humid than open air, green-tinted from
 *   filtered light through wet leaves. Fog starts closer but end is less
 *   compressed than active storm — sheltered, not stormed.
 *
 * Sky visibility is cached by player position. The cache invalidates when
 * the player moves >= CACHE_MOVE_THRESHOLD blocks — cheap enough to run
 * every frame at walking pace, essentially free at sprint.
 *
 * Only activates when humidityMass > 0.3 — dry biomes have no canopy
 * moisture to express and the modifier is a no-op in clear weather.
 *
 * Pipeline position: after WeatherFogModifier so it refines the already
 * weather-modified fog, before ValleyFogModifier so valley compression
 * still compounds correctly on top.
 */
public final class CanopyMoistureModifier implements FogModifier {

    // Movement cache threshold.
    // Smaller than ValleyFogModifier (6 blocks) — sky visibility changes
    // more sharply than terrain height, especially at forest/clearing edges.
    private static final float CACHE_MOVE_THRESHOLD = 3.0f;

    // Minimum humidity to activate either effect.
    private static final float HUMIDITY_MIN = 0.30f;

    // Exposed rain: near-fog density pull when standing in open rain.
    // Subtle — WeatherFogModifier already handles bulk compression.
    private static final float EXPOSED_START_PULL = 0.08f;

    // Sheltered canopy: fog start compression and color shift magnitudes.
    private static final float CANOPY_START_PULL   = 0.14f;
    private static final float CANOPY_END_PULL     = 0.06f;
    private static final float CANOPY_GREEN_LIFT   = 0.012f;
    private static final float CANOPY_RED_DROP     = 0.010f;
    private static final float CANOPY_BLUE_DROP    = 0.006f;

    private boolean cachedCanSeeSky = true;
    private int     cacheX          = Integer.MIN_VALUE;
    private int     cacheY          = Integer.MIN_VALUE;
    private int     cacheZ          = Integer.MIN_VALUE;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        if (!AtmosConfig.get().fog.fogEnabled) return fog;

        float humidity = env.humidityMass;
        if (humidity < HUMIDITY_MIN) return fog;

        float rain = ctx.rain();

        // No rain and low humidity — nothing for this modifier to express.
        if (rain < 0.01f && humidity < 0.55f) return fog;

        boolean canSeeSky = sampleSkyVisibilityCached(ctx);

        float start = fog.start();
        float end   = fog.end();
        float red   = fog.red();
        float green = fog.green();
        float blue  = fog.blue();

        if (canSeeSky && rain > 0.01f) {
            // --- Exposed to open rain ---
            // Near-fog start pulls slightly closer — the air immediately around
            // the player is saturated. WeatherFogModifier handles the rest.
            float humidFactor  = FogMath.clamp((humidity - HUMIDITY_MIN) / (1f - HUMIDITY_MIN), 0f, 1f);
            float rainFactor   = FogMath.smoothstep(FogMath.clamp(rain / 0.6f, 0f, 1f));
            float strength     = humidFactor * rainFactor;

            start *= FogMath.lerp(1.0f, 1.0f - EXPOSED_START_PULL, strength);
            start  = FogMath.clamp(start, 1f, end * 0.80f);

        } else if (!canSeeSky) {
            // --- Sheltered under canopy ---
            // Enclosed humid air: fog starts closer, green-tinted filtered light.
            // Effect scales with humidity — dry biome caves don't get green tint.
            // Rain amplifies the effect: dripping canopy vs dry canopy.
            float humidFactor  = FogMath.clamp((humidity - HUMIDITY_MIN) / (1f - HUMIDITY_MIN), 0f, 1f);
            float rainAmp      = FogMath.lerp(0.5f, 1.0f, FogMath.clamp(rain / 0.4f, 0f, 1f));
            float strength     = FogMath.smoothstep(humidFactor) * rainAmp;

            if (strength < 0.02f) return fog;

            end   *= FogMath.lerp(1.0f, 1.0f - CANOPY_END_PULL,   strength * (1f - fog.openness()));
            start *= FogMath.lerp(1.0f, 1.0f - CANOPY_START_PULL, strength);
            start  = FogMath.clamp(start, 1f, end * 0.75f);

            // Green-tinted enclosed air — wet leaves, filtered light.
            // Only applies in genuinely humid conditions, not desert caves.
            if (humidity > 0.50f) {
                float colorStr = strength * FogMath.clamp((humidity - 0.50f) / 0.50f, 0f, 1f);
                red   -= CANOPY_RED_DROP   * colorStr;
                green += CANOPY_GREEN_LIFT * colorStr;
                blue  -= CANOPY_BLUE_DROP  * colorStr;
            }
        }

        return fog.with(start, end,
                FogMath.clamp(red, 0f, 1f), FogMath.clamp(green, 0f, 1f), FogMath.clamp(blue, 0f, 1f));
    }

    /**
     * Returns whether the camera has a direct line to the sky.
     * Cached by block position — invalidates when the player moves
     * >= CACHE_MOVE_THRESHOLD blocks in any axis.
     */
    private boolean sampleSkyVisibilityCached(FogContext ctx) {
        BlockPos pos = ctx.camera().getBlockPosition();

        int   dx     = pos.getX() - cacheX;
        int   dy     = pos.getY() - cacheY;
        int   dz     = pos.getZ() - cacheZ;
        float distSq = dx * dx + dy * dy + dz * dz;

        if (cacheX != Integer.MIN_VALUE
                && distSq < CACHE_MOVE_THRESHOLD * CACHE_MOVE_THRESHOLD) {
            return cachedCanSeeSky;
        }

        cachedCanSeeSky = ctx.level().canSeeSky(pos);
        cacheX = pos.getX();
        cacheY = pos.getY();
        cacheZ = pos.getZ();
        return cachedCanSeeSky;
    }
}