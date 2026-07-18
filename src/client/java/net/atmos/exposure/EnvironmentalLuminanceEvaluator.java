package net.atmos.exposure;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Environmental Luminance Estimate (ELE) evaluator — Chapter 14 §14.6,
 * Appendix W §1.
 *
 * Combines two of Appendix W §1's three named inputs:
 *
 *   Global Illumination State — RawExposureFactors.thermalEnergy(), reused
 *     directly rather than resampling sun angle (EnvironmentalState already
 *     derives thermalEnergy from cos(sunAngle) via a damped drifter).
 *
 *   Directional Lighting — the Solar Position component of SunReach
 *     (Chapter 8 Stage One): clamp(cos(sunAngleRadians), 0, 1). This is
 *     the same formula net.atmos.sunreach.SunReachEvaluator uses for its
 *     own Stage One term, computed independently here rather than by
 *     calling that evaluator, because SunReachEvaluator.evaluate() also
 *     requires a HorizonMap (Stage Two, Terrain Exposure) tied to a
 *     specific Cell Grid coordinate — and ExposureInputs deliberately
 *     excludes CellGrid cell lookups (see that record's class doc). This
 *     expression already appears independently in EnvironmentalState,
 *     HeroMomentEvaluator, and DaylightFogModifier for the identical
 *     reason: none of them call into net.atmos.sunreach either.
 *
 * Local Cell State (Appendix W §1's third named input) remains
 * unimplemented — identical precedent to ExposureFactorSampler's own
 * documented CellGrid omission.
 *
 * Aggregation: weighted arithmetic sum (Appendix W §1.2 Candidate A),
 * selected because Appendix W explicitly critiques the geometric-mean and
 * product alternatives (Candidates B/C/D) for a "veto" failure mode that
 * underestimates enclosed spaces — a defect this sum does not share.
 * Flagged as implementation-defined per Appendix W §8's Decision Register.
 */
public final class EnvironmentalLuminanceEvaluator {

    private EnvironmentalLuminanceEvaluator() {}

    public static EnvironmentalLuminanceResult evaluate(RawExposureFactors factors, float sunAngleRadians) {
        float global      = factors.thermalEnergy();
        float directional = FogMath.clamp((float) Math.cos(sunAngleRadians), 0f, 1f);

        float value = FogMath.clamp(
                global      * ExposureWeights.ELE_WEIGHT_GLOBAL
                        + directional * ExposureWeights.ELE_WEIGHT_DIRECTIONAL,
                0f, 1f);

        return new EnvironmentalLuminanceResult(global, directional, value);
    }
}