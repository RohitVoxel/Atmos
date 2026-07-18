package net.atmos.exposure;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.memory.AtmosphericMemorySnapshot;

/**
 * Target Exposure evaluator — Chapter 14 §14.8 (Target Exposure), §14.21/
 * §14.27 (Memory Integration).
 *
 * Chapter 14 gives no numeric formula anywhere — §14.8 states only that
 * exposure "moves gradually toward the target." Linear interpolation
 * between two anchors is therefore an implementation choice, not a
 * verified alternative to a specified curve.
 *
 * EXPOSURE_SCALE_BRIGHT_ANCHOR / DARK_ANCHOR are NOT Chapter-14-anchored
 * values (a prior revision incorrectly cited "Chapter 9 §13," which does
 * not exist for this content). They are implementation-defined defaults
 * loosely inspired by an unrelated illustrative example in the OLD
 * guide's Chapter 4 (Confidence System) showing how Exposure Scale
 * multiplies into rendered alpha — a different system, not a Target
 * Exposure formula. Status: implementation-defined, pending Rohit's
 * explicit tuning approval — same category as ConfidenceWeights' 0.5/0.5
 * split.
 *
 * memory is nullable — same neutral-fallback contract as MemoryEvaluator.
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