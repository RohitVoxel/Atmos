package net.atmos.seasonal;

/**
 * Continuous Bias Generation — Appendix X Revision 2.7 §7 (Model A).
 *
 * Synthesizes thermalTendency/moistureTendency (owned exclusively by
 * {@link SeasonalProfileModel}) into densityBias, clarityBias, and
 * volatility. Consumes strictly the two seasonal tendency floats — never
 * dailyProgress, micro phases, mood, weather, or any downstream system.
 *
 * densityBias / clarityBias: wet -> denser, foggier atmosphere (Chapter
 * 15 §15.9/§15.16); cold -> lingering density (§15.17); warm -> faster
 * dissipation (§15.18). clarityBias is the exact inverse of densityBias.
 *
 * volatility: sin^2(theta) = 1 - cos^2(theta), reconstructed from each
 * cosine tendency without requiring the original angle — highest at the
 * "equinox" midpoints, lowest at the "solstice" extremes.
 *
 * The two weight coefficients below are deliberately private (review
 * correction) rather than published in {@link SFSConstants} — Appendix X
 * §7 explicitly delegates this transfer function's coefficients to the
 * implementer, and they remain implementation-defined and encapsulated
 * within their sole consumer until an Architect freeze promotes them to a
 * shared constant. Must sum to 1.0.
 */
public final class ContinuousBiasGenerator {

    private ContinuousBiasGenerator() {}

    private static final float DENSITY_MOISTURE_WEIGHT = 0.7f;
    private static final float DENSITY_THERMAL_WEIGHT  = 0.3f;

    public static SeasonalBiasResult evaluate(float thermalTendency, float moistureTendency) {
        float densityBias = DENSITY_MOISTURE_WEIGHT * moistureTendency
                - DENSITY_THERMAL_WEIGHT * thermalTendency;
        float clarityBias = -densityBias;

        float thermalRateSquared  = 1f - thermalTendency * thermalTendency;
        float moistureRateSquared = 1f - moistureTendency * moistureTendency;
        float volatility = (thermalRateSquared + moistureRateSquared) * 0.5f;

        return new SeasonalBiasResult(densityBias, clarityBias, volatility);
    }
}