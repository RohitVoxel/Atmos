package net.atmos.render;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Width & Length Mapping — Appendix ZB Blocker 8.
 *
 * clusterRadius > 0 is already guaranteed upstream by the Cluster Builder
 * (Chapter 7) — not re-validated here. Coefficients sourced from
 * RenderingMathConstants (Appendix ZB §III).
 *
 * Stateless, deterministic, O(1).
 */
public final class WidthLengthMappingEvaluator {

    private WidthLengthMappingEvaluator() {}

    public static WidthLengthResult evaluate(float clusterRadius, float sunAngleRadians) {
        float sunHeight = (float) Math.cos(sunAngleRadians);
        float elevationRadians = (float) Math.asin(FogMath.clamp(sunHeight, -1f, 1f));

        float width = clusterRadius * RenderingMathConstants.WIDTH_OVERLAP_SCALAR;

        float sineElevation = Math.max(
                (float) Math.sin(elevationRadians), RenderingMathConstants.LENGTH_SINE_FLOOR);
        float length = Math.min(
                RenderingMathConstants.LENGTH_MAX_ABSOLUTE, clusterRadius / sineElevation);

        return new WidthLengthResult(width, length);
    }
}