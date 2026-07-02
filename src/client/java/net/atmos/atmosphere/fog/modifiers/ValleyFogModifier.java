package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.config.AtmosConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Compresses fog when the camera is below surrounding terrain — the valley
 * pooling effect. Fog collects in depressions naturally because denser, wetter
 * air sinks into low points.
 *
 * Performance: height sampling is gated on player movement. The 8 heightmap
 * lookups only re-run when the player has moved >= CACHE_MOVE_THRESHOLD blocks
 * horizontally from the last sample position. At walking pace this is roughly
 * once every 1.5 seconds. The cached average height is reused between samples.
 *
 * Altitude ceiling raised from 100 → 200:
 * Minecraft 1.18+ terrain generates valleys, plateaus, and depressions between
 * Y=100–200. The old ceiling was written for pre-1.18 world height where Y=100
 * was reliably above all terrain. Raising it to 200 covers all realistic overworld
 * valley scenarios while the movement cache keeps sampling cost negligible.
 *
 * High-altitude attenuation:
 * Valley fog at Y=160 should feel less pronounced than at Y=64 — high terrain
 * valleys are more exposed, wind-scoured, and open than sea-level depressions.
 * An altitude factor scales strength down linearly above SEA_LEVEL (64) so
 * mountain valleys get a softer effect than lowland valleys.
 *
 * Toggle: config.fog.valleyFog
 */
public final class ValleyFogModifier implements FogModifier {

    private static final int   SAMPLE_RADIUS    = 16;
    private static final float VALLEY_FULL_DEPTH = 12f;
    private static final float MAX_DENSITY_BOOST = 0.22f;
    private static final float VALLEY_BLUE_LIFT  = 0.025f;
    private static final float VALLEY_BRIGHT     = 0.015f;

    // Raised from 100 to 200 for 1.18+ terrain.
    // Movement-based caching makes sampling cost negligible at this ceiling.
    private static final float VALLEY_ALTITUDE_CEILING = 200f;

    // Sea level reference for high-altitude attenuation.
    // Above this, valley effect scales down toward ALTITUDE_MIN_STRENGTH at ceiling.
    private static final float SEA_LEVEL            = 64f;
    private static final float ALTITUDE_MIN_STRENGTH = 0.45f;

    private static final float CACHE_MOVE_THRESHOLD = 6.0f;

    private static final int[][] OFFSETS = {
            { SAMPLE_RADIUS,  0}, {-SAMPLE_RADIUS,  0},
            { 0,  SAMPLE_RADIUS}, { 0, -SAMPLE_RADIUS},
            { SAMPLE_RADIUS,  SAMPLE_RADIUS}, {-SAMPLE_RADIUS,  SAMPLE_RADIUS},
            { SAMPLE_RADIUS, -SAMPLE_RADIUS}, {-SAMPLE_RADIUS, -SAMPLE_RADIUS},
    };

    private final EnvironmentalState envState;

    private float cachedAvgHeight = 64f;
    private int   cacheX          = Integer.MIN_VALUE;
    private int   cacheZ          = Integer.MIN_VALUE;

    private float deltaSec = 0.05f;

    public ValleyFogModifier(EnvironmentalState envState) {
        this.envState = envState;
    }

    public void setDeltaSec(float deltaSec) {
        this.deltaSec = Math.min(deltaSec, 0.1f);
    }

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        if (!AtmosConfig.get().fog.valleyFog) return fog;

        float cameraY   = ctx.cameraY();
        float rawValley = 0f;

        if (cameraY <= VALLEY_ALTITUDE_CEILING) {
            float avg   = sampleSurroundingHeightCached(ctx);
            float depth = avg - cameraY;
            if (depth > 0f) {
                rawValley = FogMath.smoothstep(FogMath.clamp(depth / VALLEY_FULL_DEPTH, 0f, 1f));

                // High-altitude attenuation: valley effect weakens above sea level.
                // Mountain valleys are more exposed than lowland depressions.
                // Scales from 1.0 at SEA_LEVEL to ALTITUDE_MIN_STRENGTH at ceiling.
                if (cameraY > SEA_LEVEL) {
                    float altitudeFactor = 1f - FogMath.clamp(
                            (cameraY - SEA_LEVEL) / (VALLEY_ALTITUDE_CEILING - SEA_LEVEL),
                            0f, 1f
                    );
                    float altitudeScale = FogMath.lerp(ALTITUDE_MIN_STRENGTH, 1.0f, altitudeFactor);
                    rawValley *= altitudeScale;
                }
            }
        }

        envState.pushValleyCompression(rawValley, deltaSec);

        float valleyFactor = env.valleyCompression;
        float humidFactor  = FogMath.clamp((env.humidityMass - 0.2f) / 0.8f, 0f, 1f);
        float strength     = valleyFactor * humidFactor;

        if (strength < 0.01f) return fog;

        float densityBoost = strength * MAX_DENSITY_BOOST;
        float end   = fog.end()   * (1f - densityBoost * 0.5f);
        float start = FogMath.clamp(fog.start() * (1f - densityBoost * 1.4f), 1f, end * 0.65f);

        float red   = FogMath.clamp(fog.red()   + VALLEY_BRIGHT    * strength, 0f, 1f);
        float green = FogMath.clamp(fog.green() + VALLEY_BRIGHT    * strength, 0f, 1f);
        float blue  = FogMath.clamp(fog.blue()  + VALLEY_BLUE_LIFT * strength, 0f, 1f);

        return fog.with(start, end, red, green, blue);
    }

    private float sampleSurroundingHeightCached(FogContext ctx) {
        BlockPos center = ctx.camera().getBlockPosition();

        int   dx     = center.getX() - cacheX;
        int   dz     = center.getZ() - cacheZ;
        float distSq = dx * dx + dz * dz;

        if (distSq < CACHE_MOVE_THRESHOLD * CACHE_MOVE_THRESHOLD) {
            return cachedAvgHeight;
        }

        cachedAvgHeight = sampleSurroundingHeight(center, ctx);
        cacheX          = center.getX();
        cacheZ          = center.getZ();
        return cachedAvgHeight;
    }

    private float sampleSurroundingHeight(BlockPos center, FogContext ctx) {
        float total = 0f;
        for (int[] o : OFFSETS) {
            total += ctx.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.getX() + o[0], center.getZ() + o[1]);
        }
        return total / OFFSETS.length;
    }
}