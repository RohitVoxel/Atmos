package net.atmos.director;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Fog Density evaluator — Appendix ZC §2 (Director-owned fogDensity),
 * consumed downstream as Appendix ZB Blocker 4's EnvironmentalVisibility.
 *
 * No numeric anchor exists anywhere in the Master Guide for this value.
 * Per Appendix ZC §2 ("Default initialization value is implementation-
 * defined and may be overridden by Director initialization logic"), this
 * evaluator derives a continuous, deterministic proxy from
 * EnvironmentalState's already-clamped humidityMass and stormEnergy — the
 * same two signals FogManager's own modifier pipeline already uses to
 * drive vanilla fog distance compression (HumidityFogModifier,
 * WeatherFogModifier).
 *
 * No re-clamping of inputs is performed — both are already guaranteed
 * within [0,1] by EnvironmentalState.advance(), the identical precedent
 * already documented by TierAEvaluator / HeroMomentEvaluator.
 *
 * Stateless, deterministic, O(1).
 */
public final class FogDensityEvaluator {

    private FogDensityEvaluator() {}

    public static float evaluate(EnvironmentalState env) {
        float humidity = env.getHumidityMass();
        float storm    = env.getStormEnergy();

        float value = humidity * DirectorWeights.FOG_DENSITY_HUMIDITY_WEIGHT
                + storm * DirectorWeights.FOG_DENSITY_STORM_WEIGHT;

        return FogMath.clamp(value, 0f, 1f);
    }
}