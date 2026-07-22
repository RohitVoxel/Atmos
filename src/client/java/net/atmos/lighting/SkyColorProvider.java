package net.atmos.lighting;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.core.CameraSnapshot;
import net.atmos.director.DirectorState;
import net.atmos.render.RenderColor;

/**
 * Sky Color Provider — Appendix ZC §3. Sole producer of C_zenith / C_horizon.
 *
 * Existing vanilla sky controllers (net.atmos.atmosphere.sky) remain
 * independent mixin-based modifiers of vanilla's own Vec3 sky color and
 * are explicitly not the source of C_zenith/C_horizon for this pipeline —
 * Appendix ZC requires a dedicated, standalone producer that no other
 * class may replicate. This class does not read, call, or modify any of
 * SkyColorController / MoonlightController / SunGlareController.
 *
 * Inputs (Appendix ZC §3): CameraSnapshot, EnvironmentalState,
 * DirectorState, sun angle. No formula for deriving C_zenith/C_horizon is
 * specified anywhere in the Master Guide or its appendices — every
 * coefficient below is implementation-defined, following the same
 * "Lighting Foundation" baseline philosophy already established by
 * WeatherAttenuationEvaluator / BiomeModifierEvaluator (Chapter 8):
 * continuous, deterministic, minimal, and documented rather than silently
 * assumed.
 *
 * Time-of-day blend reuses FogMath.horizonFactor(), the identical
 * dawn/dusk weighting already shared by DaylightFogModifier,
 * SkyColorController, and HeroMomentEvaluator (Extend Before Creating).
 *
 * Humidity/storm terms are a documented baseline analogous to
 * SkyColorController's own "Humidity haze"/"Storm" sections, independently
 * re-derived here since this producer must not read vanilla sky color.
 *
 * Altitude: CameraSnapshot.position().y() applies a bounded cold-shift
 * above sea level, mirroring HeightFogModifier's existing altitude-color
 * philosophy without depending on that fog-specific, package-private
 * class.
 *
 * Global Intensity: DirectorState.globalIntensityResult().value() scales
 * the final colors, per Appendix Q §Q.8 ("Global Intensity... published
 * read-only for future consumers") and Chapter 11 §11.26 ("Every visual
 * system scales through this value").
 *
 * Stateless, deterministic, O(1). Simulation Thread only.
 */
public final class SkyColorProvider {

    private SkyColorProvider() {}

    private static final RenderColor NIGHT_ZENITH     = new RenderColor(0.02f, 0.02f, 0.05f);
    private static final RenderColor NIGHT_HORIZON    = new RenderColor(0.05f, 0.05f, 0.10f);
    private static final RenderColor DAY_ZENITH       = new RenderColor(0.35f, 0.55f, 0.90f);
    private static final RenderColor DAY_HORIZON      = new RenderColor(0.70f, 0.80f, 0.95f);
    private static final RenderColor HORIZON_WARM     = new RenderColor(0.90f, 0.55f, 0.35f);
    private static final RenderColor ALTITUDE_COLD    = new RenderColor(0.65f, 0.75f, 0.95f);
    private static final RenderColor HAZE_COLOR       = new RenderColor(0.75f, 0.78f, 0.82f);
    private static final RenderColor STORM_SKY_COLOR  = new RenderColor(0.25f, 0.27f, 0.32f);

    private static final float HORIZON_WARM_STRENGTH = 0.55f;
    private static final float ZENITH_WARM_STRENGTH   = 0.15f;
    private static final float HAZE_STRENGTH          = 0.25f;
    private static final float STORM_STRENGTH         = 0.50f;

    private static final float ALTITUDE_START     = 140f;
    private static final float ALTITUDE_FULL      = 280f;
    private static final float ALTITUDE_MAX_BLEND = 0.35f;

    public static SkyColorResult evaluate(CameraSnapshot camera, EnvironmentalState env,
                                          DirectorState directorState, float sunAngleRadians) {
        float sunHeight     = (float) Math.cos(sunAngleRadians);
        float sinAngle      = (float) Math.sin(sunAngleRadians);
        float dayFactor     = FogMath.clamp(sunHeight, 0f, 1f);
        float horizonWarmth = FogMath.horizonFactor(sunHeight, sinAngle);

        RenderColor zenith  = lerpColor(NIGHT_ZENITH,  DAY_ZENITH,  dayFactor);
        RenderColor horizon = lerpColor(NIGHT_HORIZON, DAY_HORIZON, dayFactor);

        zenith  = lerpColor(zenith,  HORIZON_WARM, horizonWarmth * ZENITH_WARM_STRENGTH);
        horizon = lerpColor(horizon, HORIZON_WARM, horizonWarmth * HORIZON_WARM_STRENGTH);

        float humidity = env.getHumidityMass();
        if (humidity > 0f) {
            zenith  = lerpColor(zenith,  HAZE_COLOR, humidity * HAZE_STRENGTH);
            horizon = lerpColor(horizon, HAZE_COLOR, humidity * HAZE_STRENGTH);
        }

        float storm = env.getStormEnergy();
        if (storm > 0f) {
            zenith  = lerpColor(zenith,  STORM_SKY_COLOR, storm * STORM_STRENGTH);
            horizon = lerpColor(horizon, STORM_SKY_COLOR, storm * STORM_STRENGTH);
        }

        float altitude = (float) camera.position().y();
        if (altitude > ALTITUDE_START) {
            float t = FogMath.smoothstep(
                    FogMath.clamp((altitude - ALTITUDE_START) / (ALTITUDE_FULL - ALTITUDE_START), 0f, 1f))
                    * ALTITUDE_MAX_BLEND;
            zenith  = lerpColor(zenith,  ALTITUDE_COLD, t);
            horizon = lerpColor(horizon, ALTITUDE_COLD, t);
        }

        float intensity = directorState.globalIntensityResult().value();
        zenith  = scaleColor(zenith,  intensity);
        horizon = scaleColor(horizon, intensity);

        return new SkyColorResult(zenith, horizon);
    }

    private static RenderColor lerpColor(RenderColor a, RenderColor b, float t) {
        float clamped = FogMath.clamp(t, 0f, 1f);
        return new RenderColor(
                FogMath.lerp(a.red(),   b.red(),   clamped),
                FogMath.lerp(a.green(), b.green(), clamped),
                FogMath.lerp(a.blue(),  b.blue(),  clamped)
        );
    }

    private static RenderColor scaleColor(RenderColor c, float scale) {
        return new RenderColor(
                FogMath.clamp(c.red()   * scale, 0f, 1f),
                FogMath.clamp(c.green() * scale, 0f, 1f),
                FogMath.clamp(c.blue()  * scale, 0f, 1f)
        );
    }
}