package net.atmos.lighting;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.core.CameraSnapshot;
import net.atmos.director.DirectorState;
import net.atmos.render.RenderColor;

/**
 * Atmospheric Lighting Pipeline — Appendix ZB Blocker 1, Appendix ZC §3.
 *
 * Pure producer: SkyColorProvider + EnvironmentalState + DirectorState +
 * sun angle -> LightingSnapshot. Stateless, no Fabric callbacks, no
 * renderer interaction, no RenderCluster involvement, no runtime wiring.
 *
 * Per Appendix ZB's "Production Architecture & Fail-Fast Sequencing":
 * all upstream inputs are mandatory; a missing producer is an incomplete
 * architecture and must fail fast, not substitute a silent neutral
 * fallback. A null input throws immediately, matching every other
 * evaluator in this codebase.
 *
 * --- Solar availability term (documented scope decision) ---
 *
 * Appendix ZB Blocker 1 requires a SunReach [0.0,1.0] input for I_base.
 * Full terrain-aware SunReach (Chapter 8) requires a HorizonMap tied to a
 * specific Cell Grid coordinate, outside this phase's declared inputs
 * (CameraSnapshot, EnvironmentalState, DirectorState, sun angle only).
 * This pipeline reuses the position-independent Solar Position factor
 * alone — clamp(cos(sunAngleRadians), 0, 1), the exact Chapter 8 Stage
 * One formula — as the bounded light-availability term, mirroring
 * Chapter 14's own identical documented deferral (ExposureInputs /
 * EnvironmentalLightingFactorEvaluator).
 *
 * --- Formula (Appendix ZB Blocker 1) ---
 *
 *     I_base   = solarPositionFactor * max(0, 1 - E_storm * STORM_LIGHT_ATTENUATION_MAX)
 *     S_shadow = clamp(1 - (H_mass * SHADOW_HUMIDITY_WEIGHT + E_storm * SHADOW_STORM_WEIGHT), 0, 1)
 *     SkyTint     = lerp(C_horizon, C_zenith, max(0, sin(phi_elevation)))
 *     WeatherTint = lerp(DEFAULT_WEATHER_TINT, STORM_WEATHER_TINT, E_storm)
 *
 * sin(phi_elevation) = sunHeight, since phi_elevation = asin(sunHeight) by
 * construction — max(0, sunHeight) clamped is exactly solarPositionFactor,
 * reused directly rather than recomputed via asin/sin.
 *
 * Constants reused verbatim from Appendix ZB §III (RenderingMathConstants).
 */
public final class AtmosphericLightingPipeline {

    private AtmosphericLightingPipeline() {}

    private static final float STORM_LIGHT_ATTENUATION_MAX = 0.7f;
    private static final float SHADOW_HUMIDITY_WEIGHT       = 0.4f;
    private static final float SHADOW_STORM_WEIGHT           = 0.6f;

    private static final RenderColor DEFAULT_WEATHER_TINT = new RenderColor(1.0f, 1.0f, 1.0f);
    private static final RenderColor STORM_WEATHER_TINT   = new RenderColor(0.6f, 0.65f, 0.7f);

    public static LightingSnapshot evaluate(CameraSnapshot camera, EnvironmentalState env,
                                            DirectorState directorState, float sunAngleRadians) {
        if (camera == null) throw new IllegalArgumentException("camera must not be null");
        if (env == null) throw new IllegalArgumentException("env must not be null");
        if (directorState == null) throw new IllegalArgumentException("directorState must not be null");

        SkyColorResult sky = SkyColorProvider.evaluate(camera, env, directorState, sunAngleRadians);

        float sunHeight           = (float) Math.cos(sunAngleRadians);
        float solarPositionFactor = FogMath.clamp(sunHeight, 0f, 1f);
        float humidityMass        = env.getHumidityMass();
        float stormEnergy         = env.getStormEnergy();

        float lightIntensity = solarPositionFactor
                * Math.max(0f, 1f - stormEnergy * STORM_LIGHT_ATTENUATION_MAX);

        float shadowStrength = FogMath.clamp(
                1f - (humidityMass * SHADOW_HUMIDITY_WEIGHT + stormEnergy * SHADOW_STORM_WEIGHT),
                0f, 1f);

        RenderColor skyTint     = lerpColor(sky.horizon(), sky.zenith(), solarPositionFactor);
        RenderColor weatherTint = lerpColor(DEFAULT_WEATHER_TINT, STORM_WEATHER_TINT, stormEnergy);

        return new LightingSnapshot(lightIntensity, shadowStrength, skyTint, weatherTint);
    }

    private static RenderColor lerpColor(RenderColor a, RenderColor b, float t) {
        float clamped = FogMath.clamp(t, 0f, 1f);
        return new RenderColor(
                FogMath.lerp(a.red(),   b.red(),   clamped),
                FogMath.lerp(a.green(), b.green(), clamped),
                FogMath.lerp(a.blue(),  b.blue(),  clamped)
        );
    }
}