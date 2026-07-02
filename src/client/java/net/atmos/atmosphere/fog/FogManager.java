package net.atmos.atmosphere.fog;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.atmosphere.fog.modifiers.*;
import net.atmos.atmosphere.AtmosphereDrifter;
import net.atmos.config.AtmosConfig;
import net.atmos.config.FogConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.List;

public final class FogManager {

    private final FogInterpolator    interpolator   = new FogInterpolator();
    private final EnvironmentalState envState       = new EnvironmentalState();
    private final ValleyFogModifier  valleyModifier = new ValleyFogModifier(envState);
    private final FogPipeline        pipeline;

    private FogState targetState = new FogState(
            8f, 96f, 0.72f, 0.78f, 0.84f, 0.5f, 0.5f, 0.6f, 0.35f
    );

    private final AtmosphereDrifter driftEnd   = new AtmosphereDrifter( 96f, 0.9f, 2.2f);
    private final AtmosphereDrifter driftStart = new AtmosphereDrifter(  8f, 1.1f, 2.5f);

    // Color drifters matched closer to sky drifter speed (2.5/6.0) to eliminate
    // the ~70% lag that causes visible sky/fog color divergence at the horizon
    // during dawn and dusk transitions. Fog color still runs slightly behind sky
    // (ground-level air lags upper atmosphere) but no longer diverges visibly.
    private final AtmosphereDrifter driftRed   = new AtmosphereDrifter(0.72f, 2.0f, 5.0f);
    private final AtmosphereDrifter driftGreen = new AtmosphereDrifter(0.78f, 2.0f, 5.0f);
    private final AtmosphereDrifter driftBlue  = new AtmosphereDrifter(0.84f, 2.0f, 5.0f);

    private FogState renderState = targetState;

    private float smoothRain    = 0f;
    private float smoothThunder = 0f;

    private long    lastUpdateNanos = -1L;
    private static final long UPDATE_GUARD_NS = 2_000_000L;
    private boolean firstFrame = true;

    private float baseEnd = 96f;

    private static final float RD_CEILING_FACTOR = 0.92f;

    private static final float RAIN_SMOOTH_RATE    = 2.5f;
    private static final float THUNDER_SMOOTH_RATE = 4.0f;

    public FogManager() {
        pipeline = new FogPipeline(List.of(
                new RenderDistanceModifier(),
                new HeightFogModifier(),
                new AtmosphericLayerModifier(),
                new DepthFogModifier(),
                new HumidityFogModifier(),
                new DryAtmosphereModifier(),
                new DaylightFogModifier(),
                new NightFogModifier(),
                new WeatherFogModifier(),
                new CanopyMoistureModifier(),
                valleyModifier,
                new CaveFogModifier()
        ));
    }

    public void update(Camera camera, ClientLevel level) {
        long now = System.nanoTime();
        if (lastUpdateNanos >= 0 && (now - lastUpdateNanos) < UPDATE_GUARD_NS) return;

        float deltaSec = (lastUpdateNanos < 0) ? 0f : (now - lastUpdateNanos) / 1_000_000_000f;
        deltaSec = Math.min(deltaSec, 0.1f);
        lastUpdateNanos = now;

        float actualRain    = level.getRainLevel(1.0f);
        float actualThunder = level.getThunderLevel(1.0f);

        if (firstFrame) {
            smoothRain    = actualRain;
            smoothThunder = actualThunder;
        } else {
            float rainFactor    = 1f - (float) Math.exp(-RAIN_SMOOTH_RATE    * deltaSec);
            float thunderFactor = 1f - (float) Math.exp(-THUNDER_SMOOTH_RATE * deltaSec);
            smoothRain    = lerp(smoothRain,    actualRain,    rainFactor);
            smoothThunder = lerp(smoothThunder, actualThunder, thunderFactor);
        }

        FogContext ctx     = FogContext.capture(camera, level, smoothRain, smoothThunder);
        BiomeTraits traits = BiomeAtmosphereRegistry.of(ctx.biome()).fog();

        baseEnd = traits.end();

        if (firstFrame) {
            envState.snapToTargets(ctx, traits);
        }

        envState.advance(ctx, traits, deltaSec);

        interpolator.advance(ctx, targetState, deltaSec);
        FogState base = interpolator.resolve();

        valleyModifier.setDeltaSec(deltaSec);
        targetState = pipeline.run(base, ctx, envState);

        // --- Render distance ceiling ---
        float rdCeiling = ctx.renderDistance() * 16.0f * RD_CEILING_FACTOR;
        if (targetState.end() > rdCeiling) {
            float ratio       = (targetState.end() > 0f)
                    ? FogMath.clamp(targetState.start() / targetState.end(), 0.1f, 0.85f)
                    : 0.3f;
            float ceiledStart = Math.min(rdCeiling * ratio, rdCeiling * 0.85f);
            targetState = targetState.withDistances(ceiledStart, rdCeiling);
        }

        // --- Visibility floor ---
        FogConfig cfg = AtmosConfig.get().fog;
        if (cfg.visibilityFloorEnabled) {
            float effectiveFloor = Math.max(
                    baseEnd * cfg.safeVisibilityFloorFraction(),
                    cfg.safeVisibilityFloorAbsolute()
            );
            if (targetState.end() < effectiveFloor) {
                float ratio        = (targetState.end() > 0f) ? targetState.start() / targetState.end() : 0.5f;
                float flooredStart = Math.min(effectiveFloor * ratio, effectiveFloor * 0.85f);
                targetState = targetState.withDistances(flooredStart, effectiveFloor);
            }
        }

        float intensity = cfg.safeFogIntensity();
        if (intensity != 1.0f) {
            targetState = targetState.withDistances(
                    targetState.start() / intensity,
                    targetState.end()   / intensity
            );
        }

        if (firstFrame) {
            snapDrifters();
            firstFrame = false;
        }

        float smoothedEnd   = driftEnd  .advance(targetState.end(),   deltaSec);
        float smoothedStart = driftStart.advance(targetState.start(), deltaSec);
        float smoothedRed   = driftRed  .advance(targetState.red(),   deltaSec);
        float smoothedGreen = driftGreen.advance(targetState.green(), deltaSec);
        float smoothedBlue  = driftBlue .advance(targetState.blue(),  deltaSec);

        smoothedStart = Math.min(smoothedStart, smoothedEnd * 0.85f);

        renderState = new FogState(
                smoothedStart, smoothedEnd,
                smoothedRed, smoothedGreen, smoothedBlue,
                targetState.openness(),
                targetState.contrastRetention(),
                targetState.weatherSensitivity(),
                targetState.humidity()
        );
    }

    public void reset() {
        envState.reset();
        interpolator.reset();

        smoothRain      = 0f;
        smoothThunder   = 0f;
        lastUpdateNanos = -1L;
        firstFrame      = true;

        driftEnd  .snap(96f);
        driftStart.snap(8f);
        driftRed  .snap(0.72f);
        driftGreen.snap(0.78f);
        driftBlue .snap(0.84f);

        targetState = new FogState(8f, 96f, 0.72f, 0.78f, 0.84f, 0.5f, 0.5f, 0.6f, 0.35f);
        renderState = targetState;
        baseEnd     = 96f;

        FogContext.clearBiomeCache();
    }

    public EnvironmentalState getEnvState() { return envState; }

    public float getFogStart() { return renderState.start(); }
    public float getFogEnd()   { return renderState.end();   }
    public float getFogRed()   { return renderState.red();   }
    public float getFogGreen() { return renderState.green(); }
    public float getFogBlue()  { return renderState.blue();  }

    // Added for Forest Spec Task 1 (Crepuscular Rays), Fix 2.
    // renderState.openness() is already the correct value to consume: it's
    // copied straight from targetState.openness(), which itself comes from
    // interpolator.resolve()'s currentOpenness — the same hold-time +
    // hysteresis-gated blend FogInterpolator already uses for fog
    // start/end/color at biome borders. No FogModifier in the pipeline
    // changes the openness field (every FogState.with*() variant carries it
    // through unmodified), so this is exactly the value the rest of the fog
    // system already treats as authoritative — just not previously exposed
    // outside FogManager.
    public float getFogOpenness() { return renderState.openness(); }

    private void snapDrifters() {
        driftEnd  .snap(targetState.end());
        driftStart.snap(targetState.start());
        driftRed  .snap(targetState.red());
        driftGreen.snap(targetState.green());
        driftBlue .snap(targetState.blue());
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}