package net.atmos.atmosphere.fog;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.client.Minecraft;

public final class FogManager {

    private static final float BLEND_DURATION = 6.25f;

    private float fogStart, fogEnd, fogRed, fogGreen, fogBlue;
    private float fromStart, fromEnd, fromRed, fromGreen, fromBlue;
    private float toStart,   toEnd,   toRed,   toGreen,   toBlue;
    private float blendProgress = 1.0f;
    private Holder<Biome> lastBiomeHolder = null;

    // openness interpolates alongside fog values for smooth biome transitions.
    private float currentOpenness = 0.5f;
    private float fromOpenness    = 0.5f;
    private float toOpenness      = 0.5f;

    private long lastUpdateNanos = -1L;

    public void update(Camera camera, ClientLevel level) {
        long now = System.nanoTime();
        float deltaSec = (lastUpdateNanos < 0)
                ? 0.0f
                : (now - lastUpdateNanos) / 1_000_000_000.0f;
        deltaSec = Math.min(deltaSec, 0.1f);
        lastUpdateNanos = now;

        BlockPos pos = camera.getBlockPosition();
        Holder<Biome> holder = level.getBiome(pos);

        if (!holder.equals(lastBiomeHolder)) {
            fromStart    = fogStart;    fromEnd   = fogEnd;
            fromRed      = fogRed;      fromGreen = fogGreen;    fromBlue     = fogBlue;
            fromOpenness = currentOpenness;

            FogProfile profile = FogProfile.of(holder);
            toStart    = profile.start();    toEnd   = profile.end();
            toRed      = profile.red();      toGreen = profile.green(); toBlue = profile.blue();
            toOpenness = profile.openness();

            blendProgress = (lastBiomeHolder == null) ? 1.0f : 0.0f;
            lastBiomeHolder = holder;
        }

        if (blendProgress < 1.0f) {
            blendProgress = Math.min(1.0f, blendProgress + deltaSec / BLEND_DURATION);
            float t = smoothstep(blendProgress);
            fogStart        = lerp(fromStart,    toStart,    t);
            fogEnd          = lerp(fromEnd,      toEnd,      t);
            fogRed          = lerp(fromRed,      toRed,      t);
            fogGreen        = lerp(fromGreen,    toGreen,    t);
            fogBlue         = lerp(fromBlue,     toBlue,     t);
            currentOpenness = lerp(fromOpenness, toOpenness, t);
        } else {
            fogStart        = toStart;    fogEnd   = toEnd;
            fogRed          = toRed;      fogGreen = toGreen;   fogBlue = toBlue;
            currentOpenness = toOpenness;
        }

        applyTimeOfDay(level);
        applyRenderDistance();
        applyDepthLayering();
        applyWeather(level);
        applyHeight(camera);
    }

    // -----------------------------------------------------------------------

    private void applyRenderDistance() {
        int rd = Minecraft.getInstance().options.renderDistance().get();
        float rdScale  = Math.clamp(rd / 12.0f, 0.6f, 2.0f);
        float rdBlocks = rd * 16.0f;

        fogEnd   = Math.min(fogEnd   * rdScale, rdBlocks * 0.85f);
        fogStart = Math.min(fogStart * rdScale, fogEnd   * 0.6f);
    }

    /**
     * Open biomes (desert, ocean, plains) push fog start further from the camera,
     * creating clear foreground and a soft atmospheric gradient toward the horizon.
     * Enclosed biomes (jungle, swamp, nether) keep fog start close for density.
     *
     * openness 0.0: no adjustment — fog starts close, dense feel preserved.
     * openness 1.0: fog start pushed to 45% of fog end — strong depth separation.
     */
    private void applyDepthLayering() {
        // Target fog start as a fraction of fog end, driven by openness.
        // At openness 0: start stays at its current ratio.
        // At openness 1: start is pushed to 45% of end.
        float currentRatio = (fogEnd > 0) ? fogStart / fogEnd : 0.3f;
        float targetRatio  = lerp(currentRatio, 0.45f, currentOpenness);

        fogStart = fogEnd * targetRatio;
    }

    private void applyWeather(ClientLevel level) {
        float rain    = level.getRainLevel(1.0f);
        float thunder = level.getThunderLevel(1.0f);

        if (rain <= 0.0f) return;

        fogEnd   *= lerp(1.0f, 0.82f, rain);
        fogStart *= lerp(1.0f, 0.88f, rain);
        fogEnd   *= lerp(1.0f, 0.88f, thunder);

        fogRed   -= 0.04f * rain;
        fogGreen += 0.01f * rain;
        fogBlue  += 0.04f * rain;
        fogRed   -= 0.03f * thunder;
        fogGreen -= 0.03f * thunder;
        fogBlue  -= 0.02f * thunder;

        fogRed   = Math.max(0.0f, fogRed);
        fogGreen = Math.max(0.0f, fogGreen);
        fogBlue  = Math.max(0.0f, fogBlue);
    }

    private void applyHeight(Camera camera) {
        float y           = (float) camera.getPosition().y;
        float heightFactor = Math.clamp((y - 62.0f) / 140.0f, -1.0f, 1.0f);
        float t            = (heightFactor + 1.0f) * 0.5f;

        fogEnd   *= lerp(0.88f, 1.14f, t);
        fogStart *= lerp(0.90f, 1.10f, t);

        fogBlue += 0.03f * heightFactor;
        fogRed  -= 0.02f * heightFactor;

        fogRed  = Math.clamp(fogRed,  0.0f, 1.0f);
        fogBlue = Math.clamp(fogBlue, 0.0f, 1.0f);
    }

    private void applyTimeOfDay(ClientLevel level) {
        float angle     = level.getSunAngle(1.0f);
        float dayFactor  = Math.max(0.0f, (float) Math.cos(angle * 0.5f));
        float duskFactor = Math.max(0.0f, (float) Math.sin(angle)) * (1.0f - dayFactor);

        fogEnd   *= lerp(0.90f, 1.08f, dayFactor);
        fogStart *= lerp(0.92f, 1.05f, dayFactor);

        fogRed   = Math.min(1.0f, fogRed   + 0.035f * duskFactor);
        fogGreen = Math.min(1.0f, fogGreen + 0.010f * duskFactor);
        fogBlue  = Math.min(1.0f, fogBlue  + 0.025f * (1.0f - dayFactor));
    }

    // -----------------------------------------------------------------------

    public float getFogStart() { return fogStart; }
    public float getFogEnd()   { return fogEnd;   }
    public float getFogRed()   { return fogRed;   }
    public float getFogGreen() { return fogGreen; }
    public float getFogBlue()  { return fogBlue;  }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smoothstep(float t) { return t * t * (3.0f - 2.0f * t); }
}