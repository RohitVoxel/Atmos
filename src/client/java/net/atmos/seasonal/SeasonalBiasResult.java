package net.atmos.seasonal;

/**
 * Explainable output of one {@link ContinuousBiasGenerator} evaluation —
 * Appendix X Revision 2.7 §7 (Model A). Owns exactly the three fields
 * Continuous Bias Generation is responsible for; thermalTendency and
 * moistureTendency remain owned by {@link SeasonalProfileModel} and are
 * not duplicated here.
 */
public record SeasonalBiasResult(
        float densityBias,
        float clarityBias,
        float volatility
) {
    public SeasonalBiasResult {
        requireFinite("densityBias", densityBias);
        requireFinite("clarityBias", clarityBias);
        requireFinite("volatility", volatility);
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
    }
}