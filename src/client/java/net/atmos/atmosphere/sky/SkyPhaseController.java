package net.atmos.atmosphere.sky;

import net.atmos.atmosphere.AtmosphereDrifter;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.config.AtmosConfig;
import net.atmos.config.AtmosReloadable;
import net.atmos.config.SkyPhaseConfig;
import net.atmos.render.RenderColor;

public final class SkyPhaseController implements AtmosReloadable {

    private static final float NIGHT_BRIGHTNESS_FLOOR_MAX = 0.09f;
    private static final float HORIZON_WEIGHT_FALLOFF_DEGREES = 25f;
    private static final float HORIZON_WEIGHT_FLOOR = 0.15f;
    private static final float STORM_DESATURATION_MAX = 0.35f;

    private final AtmosphereDrifter driftR = new AtmosphereDrifter(0.33f, 2.2f, 5.5f);
    private final AtmosphereDrifter driftG = new AtmosphereDrifter(0.56f, 2.2f, 5.5f);
    private final AtmosphereDrifter driftB = new AtmosphereDrifter(0.92f, 2.2f, 5.5f);

    private boolean firstFrame = true;
    private boolean advancedThisFrame = false;

    private SkyPhase lastPhase = SkyPhase.DAY;

    public void beginFrame() {
        advancedThisFrame = false;
    }

    public int apply(int vanillaSkyColor, FogContext ctx, EnvironmentalState env, float deltaSec) {
        SkyPhaseConfig cfg = AtmosConfig.get().skyPhase;
        if (!cfg.enhancedSkyEnabled) return vanillaSkyColor;

        float elevationDegrees = elevationDegrees(ctx.sunAngle());
        SkyPhaseResult result = SkyPhaseModel.evaluate(elevationDegrees);
        lastPhase = result.phase();

        RenderColor blended = blendZenithHorizon(result, elevationDegrees);
        blended = applyTwilightIntensity(blended, elevationDegrees, cfg.safeTwilightIntensity());
        blended = applyStormDesaturation(blended, env.getStormEnergy());
        blended = applyNightBrightness(blended, elevationDegrees, cfg.safeNightBrightness());

        float scaledDelta = Math.max(0f, deltaSec) * cfg.safeTransitionSpeedMultiplier();

        if (firstFrame) {
            driftR.snap(blended.red());
            driftG.snap(blended.green());
            driftB.snap(blended.blue());
            firstFrame = false;
            advancedThisFrame = true;
        }

        float smoothR, smoothG, smoothB;
        if (!advancedThisFrame) {
            smoothR = driftR.advance(blended.red(), scaledDelta);
            smoothG = driftG.advance(blended.green(), scaledDelta);
            smoothB = driftB.advance(blended.blue(), scaledDelta);
            advancedThisFrame = true;
        } else {
            smoothR = driftR.get();
            smoothG = driftG.get();
            smoothB = driftB.get();
        }

        return blendWithVanilla(vanillaSkyColor, smoothR, smoothG, smoothB, cfg.safeSkyColorIntensity());
    }

    public void reset() {
        firstFrame = true;
        advancedThisFrame = false;
        lastPhase = SkyPhase.DAY;
    }

    /** AtmosReloadable — Sky Phase reads AtmosConfig live every frame; nothing cached to invalidate. */
    @Override
    public void onConfigReload() {
    }

    public SkyPhase currentPhase() {
        return lastPhase;
    }

    private static float elevationDegrees(float sunAngleRadians) {
        float sunHeight = (float) Math.cos(sunAngleRadians);
        return (float) Math.toDegrees(Math.asin(FogMath.clamp(sunHeight, -1f, 1f)));
    }

    private static RenderColor blendZenithHorizon(SkyPhaseResult result, float elevationDegrees) {
        float horizonWeight = FogMath.clamp(
                1f - Math.abs(elevationDegrees) / HORIZON_WEIGHT_FALLOFF_DEGREES,
                HORIZON_WEIGHT_FLOOR, 1f);

        return new RenderColor(
                FogMath.lerp(result.zenith().red(),   result.horizon().red(),   horizonWeight),
                FogMath.lerp(result.zenith().green(), result.horizon().green(), horizonWeight),
                FogMath.lerp(result.zenith().blue(),  result.horizon().blue(),  horizonWeight)
        );
    }

    private static RenderColor applyTwilightIntensity(RenderColor color, float elevationDegrees, float twilightIntensity) {
        if (elevationDegrees < SkyPhase.NIGHT_START_DEGREES || elevationDegrees > SkyPhase.CIVIL_TWILIGHT_START_DEGREES) {
            return color;
        }
        float gray = luminance(color);
        return new RenderColor(
                FogMath.lerp(gray, color.red(),   twilightIntensity),
                FogMath.lerp(gray, color.green(), twilightIntensity),
                FogMath.lerp(gray, color.blue(),  twilightIntensity)
        );
    }

    private static RenderColor applyStormDesaturation(RenderColor color, float stormEnergy) {
        if (stormEnergy <= 0f) return color;
        float gray = luminance(color) * 0.85f;
        float strength = stormEnergy * STORM_DESATURATION_MAX;
        return new RenderColor(
                FogMath.lerp(color.red(),   gray, strength),
                FogMath.lerp(color.green(), gray, strength),
                FogMath.lerp(color.blue(),  gray, strength)
        );
    }

    private static RenderColor applyNightBrightness(RenderColor color, float elevationDegrees, float nightBrightness) {
        if (elevationDegrees > SkyPhase.ASTRONOMICAL_TWILIGHT_START_DEGREES) return color;

        float floor = nightBrightness * NIGHT_BRIGHTNESS_FLOOR_MAX;
        float luminance = luminance(color);
        if (luminance >= floor || luminance <= 0f) return color;

        float scale = floor / luminance;
        return new RenderColor(
                Math.min(1f, color.red()   * scale),
                Math.min(1f, color.green() * scale),
                Math.min(1f, color.blue()  * scale)
        );
    }

    private static float luminance(RenderColor c) {
        return c.red() * 0.299f + c.green() * 0.587f + c.blue() * 0.114f;
    }

    private static int blendWithVanilla(int vanillaSkyColor, float phaseR, float phaseG, float phaseB, float intensity) {
        float vr = ((vanillaSkyColor >> 16) & 0xFF) / 255f;
        float vg = ((vanillaSkyColor >>  8) & 0xFF) / 255f;
        float vb = ( vanillaSkyColor        & 0xFF) / 255f;

        float outR = FogMath.clamp(vr + (phaseR - vr) * intensity, 0f, 1f);
        float outG = FogMath.clamp(vg + (phaseG - vg) * intensity, 0f, 1f);
        float outB = FogMath.clamp(vb + (phaseB - vb) * intensity, 0f, 1f);

        return ((int) (outR * 255f) << 16) | ((int) (outG * 255f) << 8) | (int) (outB * 255f);
    }
}