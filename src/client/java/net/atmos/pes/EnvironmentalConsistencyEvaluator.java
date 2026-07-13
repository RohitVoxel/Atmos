package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.composition.Composition;

/**
 * Environmental Consistency evaluator — Chapter 12 §12.11.
 *
 * Checks whether composition density agrees with the actual, dynamic
 * EnvironmentalState.humidityMass — not the biome's static identity
 * (that comparison belongs to BiomeIdentityEvaluator, §12.12).
 *
 * Depth (§12.19), Color Harmony (§12.17), and Contrast (§12.18) are not
 * evaluated here — all three require Exposure Model output (Chapter 14,
 * unbuilt).
 */
public final class EnvironmentalConsistencyEvaluator {

    private EnvironmentalConsistencyEvaluator() {}

    public static EnvironmentalConsistencyResult evaluate(EnvironmentalState env, Composition composition) {
        float actualHumidity = env.getHumidityMass();
        float densitySignal  = PESMath.compositionDensitySignal(composition);

        float deviation = Math.abs(densitySignal - actualHumidity);
        float value = PESMath.deviationScore(densitySignal, actualHumidity,
                PESWeights.ENVIRONMENTAL_CONSISTENCY_TOLERANCE);

        return new EnvironmentalConsistencyResult(deviation, value, PESMath.passesCategoryThreshold(value));
    }
}