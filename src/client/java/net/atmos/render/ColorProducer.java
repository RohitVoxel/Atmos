package net.atmos.render;

import net.atmos.lighting.LightingSnapshot;

/**
 * Color Producer — Appendix ZB Blocker 2, Appendix ZD §2.
 *
 * Per Appendix ZD §2 and the Single Producer Rule (Appendix P), no separate
 * Sun Color / Fog Color producer exists or is introduced here. C_sun and
 * C_fog are derived internally, consuming LightingSnapshot only, sourced
 * directly from the fields AtmosphericLightingPipeline (via
 * SkyColorProvider) already publishes for this purpose:
 *
 *     C_sun = LightingSnapshot.skyTint()
 *     C_fog = LightingSnapshot.weatherTint()
 *
 * The Blocker 2 formula is then applied unmodified:
 *
 *     C_direct  = C_sun * skyTint
 *     C_ambient = C_fog * weatherTint
 *     C_raw     = C_direct * lightIntensity + C_ambient * (1 - lightIntensity)
 *     L         = dot(C_raw, LUMINANCE_WEIGHTS)
 *     C_final   = C_raw / (1 + max(0, L - 1))
 *
 * Per Blocker 2 §5, every upstream tint is already normalized to [0,1], so
 * no re-clamping is performed before the final luminance-scaling division.
 * Luminance weights are read from RenderingMathConstants.
 *
 * Stateless, deterministic, O(1). Does not construct RenderCluster.
 */
public final class ColorProducer {

    private ColorProducer() {}

    public static ColorResult evaluate(LightingSnapshot lighting) {
        RenderColor sunColor = lighting.skyTint();
        RenderColor fogColor = lighting.weatherTint();

        RenderColor direct  = multiply(sunColor, lighting.skyTint());
        RenderColor ambient = multiply(fogColor, lighting.weatherTint());

        float intensity = lighting.lightIntensity();
        RenderColor raw = new RenderColor(
                direct.red()   * intensity + ambient.red()   * (1f - intensity),
                direct.green() * intensity + ambient.green() * (1f - intensity),
                direct.blue()  * intensity + ambient.blue()  * (1f - intensity)
        );

        float luminance = raw.red()   * RenderingMathConstants.LUMINANCE_WEIGHT_RED
                + raw.green() * RenderingMathConstants.LUMINANCE_WEIGHT_GREEN
                + raw.blue()  * RenderingMathConstants.LUMINANCE_WEIGHT_BLUE;

        float scale = 1f + Math.max(0f, luminance - 1f);

        RenderColor finalColor = new RenderColor(
                raw.red()   / scale,
                raw.green() / scale,
                raw.blue()  / scale
        );

        return new ColorResult(finalColor, luminance);
    }

    private static RenderColor multiply(RenderColor a, RenderColor b) {
        return new RenderColor(a.red() * b.red(), a.green() * b.green(), a.blue() * b.blue());
    }
}