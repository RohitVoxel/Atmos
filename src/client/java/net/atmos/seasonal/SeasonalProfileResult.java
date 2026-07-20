package net.atmos.seasonal;

/**
 * Output of one {@link SeasonalProfileModel} evaluation — Appendix X
 * Revision 2.7 §6. Owns exactly the two fields Seasonal Profile Model is
 * responsible for, published downstream as-is inside
 * {@link SeasonalFeelingSnapshot#thermalTendency()} /
 * {@link SeasonalFeelingSnapshot#moistureTendency()}.
 */
public record SeasonalProfileResult(
        float thermalTendency,
        float moistureTendency
) {
    public SeasonalProfileResult {
        requireBounded("thermalTendency", thermalTendency);
        requireBounded("moistureTendency", moistureTendency);
    }

    private static void requireBounded(String name, float value) {
        if (!Float.isFinite(value) || value < -1f || value > 1f) {
            throw new IllegalArgumentException(name + " must be within [-1,1], got " + value);
        }
    }
}