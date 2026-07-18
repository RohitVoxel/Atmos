package net.atmos.exposure;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Environmental Lighting Factor evaluator — Chapter 14 §14.6, Appendix W §1.
 *
 * Derives the two of Appendix W §1's three named inputs that have an
 * existing data source, without combining them (see
 * {@link EnvironmentalLightingFactors}'s class doc for why):
 *
 *   Global Illumination State — RawExposureFactors.thermalEnergy(), reused
 *     directly rather than resampling sun angle (EnvironmentalState already
 *     derives thermalEnergy from cos(sunAngle) via a damped drifter).
 *
 *   Directional Lighting — the Solar Position component of SunReach
 *     (Chapter 8 Stage One): clamp(cos(sunAngleRadians), 0, 1). Computed
 *     independently here rather than by calling SunReachEvaluator, because
 *     SunReachEvaluator.evaluate() also requires a HorizonMap (Stage Two,
 *     Terrain Exposure) tied to a specific Cell Grid coordinate — and
 *     ExposureInputs deliberately excludes CellGrid cell lookups (see that
 *     record's class doc). This expression already appears independently
 *     in EnvironmentalState, HeroMomentEvaluator, and DaylightFogModifier
 *     for the identical reason: none of them call into net.atmos.sunreach
 *     either.
 *
 * Local Cell State (Appendix W §1's third named input) remains
 * unimplemented — identical precedent to ExposureFactorSampler's own
 * documented CellGrid omission.
 *
 * Produces no combined luminance value — see {@link EnvironmentalLightingFactors}
 * and {@link TargetExposureEvaluator} for the deferred-aggregation rationale
 * (Appendix W §8).
 */
public final class EnvironmentalLightingFactorEvaluator {

    private EnvironmentalLightingFactorEvaluator() {}

    public static EnvironmentalLightingFactors evaluate(RawExposureFactors factors, float sunAngleRadians) {
        float global      = factors.thermalEnergy();
        float directional = FogMath.clamp((float) Math.cos(sunAngleRadians), 0f, 1f);

        return new EnvironmentalLightingFactors(global, directional);
    }
}