package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.composition.Composition;

/**
 * Weather Identity evaluator — Chapter 12 §12.13.
 *
 * Checks composition density against EnvironmentalState.stormEnergy — the
 * density half of §12.13's "exposure and cluster density... align with
 * the active weather state" (the exposure half is unavailable, Chapter 14
 * unbuilt). Distinct from EnvironmentalConsistencyEvaluator, which
 * compares density against humidity rather than storm intensity.
 */
public final class WeatherIdentityEvaluator {

    private WeatherIdentityEvaluator() {}

    public static WeatherIdentityResult evaluate(EnvironmentalState env, Composition composition) {
        float stormEnergy   = env.getStormEnergy();
        float densitySignal = PESMath.compositionDensitySignal(composition);

        float deviation = Math.abs(densitySignal - stormEnergy);
        float value = PESMath.deviationScore(densitySignal, stormEnergy,
                PESWeights.WEATHER_IDENTITY_TOLERANCE);

        return new WeatherIdentityResult(deviation, value, PESMath.passesCategoryThreshold(value));
    }
}