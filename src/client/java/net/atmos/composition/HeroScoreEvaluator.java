package net.atmos.composition;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cluster.Cluster;
import net.atmos.core.CameraSnapshot;

/**
 * Hero Score evaluator (Chapter 10 Part 3). See {@link HeroScoreResult} and
 * {@link CompositionWeights} for the documented scope reduction from
 * Chapter 10 Part 3's six-factor formula to the three factors implemented
 * here.
 */
public final class HeroScoreEvaluator {

    private HeroScoreEvaluator() {}

    public static HeroScoreResult evaluate(Cluster candidate,
                                           float confidence,
                                           CameraSnapshot camera,
                                           float atmosphericValueMean) {
        float distance = (float) camera.position().distanceTo(candidate.centerWorldPos());

        float depthQuality = depthQualityFactor(distance);
        float uniqueness   = uniquenessFactor(candidate, atmosphericValueMean);

        float value = confidence * depthQuality * uniqueness;

        return new HeroScoreResult(confidence, depthQuality, uniqueness, value);
    }

    /**
     * Depth Quality transfer function over Chapter 10 Part 3's documented
     * bands (Near 6–15 too close, Medium 20–45 ideal, Far 50–80 acceptable,
     * Extreme 80+ penalty). The interpolation shape between anchor bands is
     * implementation-defined — Chapter 10 gives qualitative labels, not an
     * exact curve — using continuous smoothstep transitions per Chapter 4
     * §2's rejection of binary/discontinuous logic.
     */
    private static float depthQualityFactor(float distance) {
        if (distance < CompositionWeights.DEPTH_NEAR_MIN) {
            return CompositionWeights.DEPTH_NEAR_FLOOR;
        }
        if (distance < CompositionWeights.DEPTH_IDEAL_MIN) {
            float t = (distance - CompositionWeights.DEPTH_NEAR_MIN)
                    / (CompositionWeights.DEPTH_IDEAL_MIN - CompositionWeights.DEPTH_NEAR_MIN);
            return FogMath.lerp(CompositionWeights.DEPTH_NEAR_FLOOR, 1f, FogMath.smoothstep(t));
        }
        if (distance <= CompositionWeights.DEPTH_IDEAL_MAX) {
            return 1f;
        }
        if (distance <= CompositionWeights.DEPTH_FAR_MAX) {
            float t = (distance - CompositionWeights.DEPTH_IDEAL_MAX)
                    / (CompositionWeights.DEPTH_FAR_MAX - CompositionWeights.DEPTH_IDEAL_MAX);
            return FogMath.lerp(1f, CompositionWeights.DEPTH_ACCEPTABLE_FLOOR, FogMath.smoothstep(t));
        }
        float t = FogMath.clamp(
                (distance - CompositionWeights.DEPTH_FAR_MAX) / CompositionWeights.DEPTH_EXTREME_RANGE,
                0f, 1f);
        return FogMath.lerp(CompositionWeights.DEPTH_ACCEPTABLE_FLOOR,
                CompositionWeights.DEPTH_EXTREME_FLOOR, FogMath.smoothstep(t));
    }

    /**
     * Uniqueness transfer function: deviation of this candidate's
     * atmospheric value from the mean across all viable candidates this
     * cycle. Implementation-defined — see {@link CompositionWeights} for
     * why width/brightness/angle (Chapter 10 Part 3's named factors) are
     * not used.
     */
    private static float uniquenessFactor(Cluster candidate, float atmosphericValueMean) {
        float deviation = Math.abs(candidate.averageAtmosphericValue() - atmosphericValueMean);
        return FogMath.clamp(
                deviation / CompositionWeights.UNIQUENESS_NORMALIZATION_RANGE,
                CompositionWeights.UNIQUENESS_FLOOR, 1f);
    }
}