package net.atmos.atmosphere.fog;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public record FogContext(
        Camera        camera,
        ClientLevel   level,
        Holder<Biome> biome,
        float         cameraY,
        float         rain,
        float         thunder,
        float         sunAngle,
        int           renderDistance
) {
    private static final int SAMPLE_RADIUS = 8;
    private static final int SAMPLE_STEP   = 4;

    private static final float SPEED_WALK     =  5f;
    private static final float SPEED_FAST     = 20f;
    private static final float THRESHOLD_WALK =  4f;
    private static final float THRESHOLD_FAST = 12f;

    // Frame guard for speed updates.
    // FogContext.capture() is called twice per frame — once for skyContext in
    // AtmosClient.START, then again inside FogManager.update(). Without this
    // guard, updateSmoothedSpeed() fires twice per frame, doubling the effective
    // smoothing rate. FogManager's UPDATE_GUARD_NS blocks the third call from
    // FogMixin but not the second. 2ms matches FogManager's own guard window.
    private static final long SPEED_UPDATE_GUARD_NS = 2_000_000L;
    private static long  lastSpeedNanos = -1L;

    private static Holder<Biome> cachedBiome = null;
    private static int           cacheX      = Integer.MIN_VALUE;
    private static int           cacheZ      = Integer.MIN_VALUE;

    // Single authoritative smoothed speed for the entire atmosphere system.
    // Read by FogInterpolator via getSmoothedSpeed() so both the biome cache
    // threshold and the blend hold time use an identical speed estimate.
    private static float smoothedSpeed = 0f;

    public static FogContext capture(Camera camera, ClientLevel level,
                                     float smoothRain, float smoothThunder) {
        updateSmoothedSpeed();
        Holder<Biome> biome = dominantBiomeCached(camera.getBlockPosition(), level);
        float cameraY       = (float) camera.getPosition().y;
        float sunAngle      = level.getSunAngle(1.0f);
        int   rd            = Minecraft.getInstance().options.renderDistance().get();

        return new FogContext(camera, level, biome, cameraY,
                smoothRain, smoothThunder, sunAngle, rd);
    }

    public static FogContext capture(Camera camera, ClientLevel level) {
        return capture(camera, level,
                level.getRainLevel(1.0f),
                level.getThunderLevel(1.0f));
    }

    public static void clearBiomeCache() {
        cachedBiome   = null;
        cacheX        = Integer.MIN_VALUE;
        cacheZ        = Integer.MIN_VALUE;
        smoothedSpeed = 0f;
        lastSpeedNanos = -1L;
    }

    /**
     * Returns the single authoritative smoothed player speed (blocks/sec).
     * FogInterpolator reads this to compute adaptive hold times so that
     * both the biome cache threshold and the blend commit timing derive
     * from the same speed estimate.
     */
    public static float getSmoothedSpeed() {
        return smoothedSpeed;
    }

    /**
     * Updates smoothedSpeed at most once per frame.
     *
     * Guard: SPEED_UPDATE_GUARD_NS (2ms) prevents the double-update that
     * occurs when capture() is called for skyContext and then again inside
     * FogManager.update() within the same render frame.
     *
     * Frame-rate independence: uses exponential smoothing with deltaSec
     * computed from nanotime. Build rate 3.0/sec and decay rate 9.0/sec
     * are equivalent to the previous 0.05/0.15 per-frame lerp factors at
     * 60fps while remaining correct at any framerate.
     *
     *   build: 1 - exp(-3.0 * dt) ≈ 0.049 at 60fps  (was 0.05 hardcoded)
     *   decay: 1 - exp(-9.0 * dt) ≈ 0.140 at 60fps  (was 0.15 hardcoded)
     *
     * Asymmetric rates are intentional: slow build resists firework boost
     * spikes; fast decay tracks genuine deceleration promptly.
     */
    private static void updateSmoothedSpeed() {
        long now = System.nanoTime();
        if (lastSpeedNanos >= 0 && (now - lastSpeedNanos) < SPEED_UPDATE_GUARD_NS) return;

        float deltaSec = (lastSpeedNanos < 0)
                ? 0f
                : Math.min((now - lastSpeedNanos) / 1_000_000_000f, 0.1f);
        lastSpeedNanos = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            smoothedSpeed = 0f;
            return;
        }

        double dx = mc.player.getDeltaMovement().x;
        double dz = mc.player.getDeltaMovement().z;
        float rawSpeed = (float) Math.sqrt(dx * dx + dz * dz) * 20f;

        if (deltaSec <= 0f) return;

        float buildFactor = 1f - (float) Math.exp(-3.0f * deltaSec);
        float decayFactor = 1f - (float) Math.exp(-9.0f * deltaSec);

        smoothedSpeed = rawSpeed > smoothedSpeed
                ? FogMath.lerp(smoothedSpeed, rawSpeed, buildFactor)
                : FogMath.lerp(smoothedSpeed, rawSpeed, decayFactor);
    }

    private static float currentCacheThreshold() {
        float t = FogMath.clamp((smoothedSpeed - SPEED_WALK) / (SPEED_FAST - SPEED_WALK), 0f, 1f);
        return FogMath.lerp(THRESHOLD_WALK, THRESHOLD_FAST, t);
    }

    private static Holder<Biome> dominantBiomeCached(BlockPos center, ClientLevel level) {
        int   dx        = center.getX() - cacheX;
        int   dz        = center.getZ() - cacheZ;
        float distSq    = dx * dx + dz * dz;
        float threshold = currentCacheThreshold();

        if (cachedBiome != null && distSq < threshold * threshold) {
            return cachedBiome;
        }

        cachedBiome = dominantBiome(center, level);
        cacheX      = center.getX();
        cacheZ      = center.getZ();
        return cachedBiome;
    }

    private static Holder<Biome> dominantBiome(BlockPos center, ClientLevel level) {
        Holder<Biome>[] seen   = new Holder[16];
        int[]           counts = new int[16];
        int             slots  = 0;

        for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; x += SAMPLE_STEP) {
            for (int z = -SAMPLE_RADIUS; z <= SAMPLE_RADIUS; z += SAMPLE_STEP) {
                Holder<Biome> b = level.getBiome(center.offset(x, 0, z));
                int idx = -1;
                for (int i = 0; i < slots; i++) {
                    if (seen[i].equals(b)) { idx = i; break; }
                }
                if (idx == -1 && slots < seen.length) {
                    seen[slots] = b;
                    idx = slots++;
                }
                if (idx != -1) counts[idx]++;
            }
        }

        if (slots == 0) return level.getBiome(center);

        int best = 0;
        for (int i = 1; i < slots; i++) {
            if (counts[i] > counts[best]) best = i;
        }
        return seen[best];
    }
}