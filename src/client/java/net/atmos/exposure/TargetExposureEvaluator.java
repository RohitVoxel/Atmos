package net.atmos.exposure;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.memory.AtmosphericMemorySnapshot;

/**
 * Target Exposure evaluator — Chapter 14 §14.7, §14.9.
 *
 * Maps EnvironmentalLuminanceResult onto an exposure-scale target using
 * the two anchors given explicitly by Chapter 9 §13 (Bright Noon = 0.65,
 * Dusk = 1.70) — reused directly rather than inventing an unanchored
 * curve. Residual Atmospheric Memory (humidity/storm) biases the target
 * upward, reproducing §14.9's post-storm example: light stays diffused
 * (higher exposure scale) even once luminance alone says "clear."
 *
 * memory is nullable — treated identically to MemoryEvaluator's own
 * established nullable-safe, neutral-fallback contract.
 *
 * Stateless, deterministic, O(1). No upper clamp is applied to the
 * result: RenderCluster.exposureScale() itself specifies no upper bound
 * (see that record's own doc), so none is invented here.
 */
public final class TargetExposureEvaluator {

    private TargetExposureEvaluator() {}

    public static TargetExposureResult evaluate(EnvironmentalLuminanceResult luminance,
                                                AtmosphericMemorySnapshot memory) {
        float luminanceExposure = FogMath.lerp(
                ExposureWeights.EXPOSURE_SCALE_DARK_ANCHOR,
                ExposureWeights.EXPOSURE_SCALE_BRIGHT_ANCHOR,
                luminance.value());

        float memorySignal = memorySignal(memory);
        float memoryBias = memorySignal * ExposureWeights.MEMORY_BIAS_MAX;

        float value = luminanceExposure + memoryBias;

        return new TargetExposureResult(luminanceExposure, memorySignal, memoryBias, value);
    }

    private static float memorySignal(AtmosphericMemorySnapshot memory) {
        if (memory == null) return 0f;
        return Math.max(memory.humidityMemory(), memory.stormMemory());
    }
}