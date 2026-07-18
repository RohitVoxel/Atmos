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
 * --- Stage 3: aggregation-operator deferral (Appendix W §8) ---
 *
 * EnvironmentalLightingFactors.directionalFactor() (the SunReach Solar
 * Position term added in Stage 3) is deliberately NOT consumed here.
 * Appendix W §1.2 explicitly leaves the operator that would combine
 * globalFactor and directionalFactor into one luminance estimate
 * unresolved (Candidates A-E are discussed, none approved). Choosing one
 * now — even a simple weighted sum — would make that Architect-reserved
 * decision inside a Stage 3 task scoped only to "wire SunReach in as an
 * input." This evaluator therefore continues to read only
 * globalFactor(), exactly as it did before directionalFactor existed:
 * Stage 3 changes what data is available, not how target exposure is
 * computed from it. directionalFactor remains accessible on the
 * EnvironmentalLightingFactors passed in (and on
 * ExposureModel.lastLightingFactors()) for inspection once a future stage
 * resolves the aggregation operator.
 *
 * EXPOSURE_SCALE_BRIGHT_ANCHOR / DARK_ANCHOR are implementation-defined
 * defaults, not literal Chapter-14 formula anchors — status:
 * implementation-defined, pending explicit tuning approval, same category
 * as ConfidenceWeights' 0.5/0.5 split.
 *
 * memory is nullable — same neutral-fallback contract as MemoryEvaluator.
 */
public final class TargetExposureEvaluator {

    private TargetExposureEvaluator() {}

    public static TargetExposureResult evaluate(EnvironmentalLightingFactors factors,
                                                AtmosphericMemorySnapshot memory) {
        float luminanceExposure = FogMath.lerp(
                ExposureWeights.EXPOSURE_SCALE_DARK_ANCHOR,
                ExposureWeights.EXPOSURE_SCALE_BRIGHT_ANCHOR,
                factors.globalFactor());

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