package net.atmos.director;

/**
 * Immutable performance-driven allocation multipliers — Appendix T §T.18.
 *
 * Every value is a pure function of the current
 * {@link net.atmos.aps.OptimizationPlan} (§T.20–§T.21). This record
 * carries no simulation meaning of its own; which visual channel each
 * multiplier scales is left to future consumer chapters (§T.19).
 *
 * heroMultiplier is floored at {@link DirectorWeights#HERO_MULTIPLIER_FLOOR}
 * (§T.9, §T.12) so Hero Moments are never fully suppressed by budget
 * alone. The remaining five multipliers are unfloored linear reads of
 * budget (§T.13–§T.17).
 */
public record DirectorPerformanceState(
        float heroMultiplier,
        float ambientMultiplier,
        float secondaryMultiplier,
        float distanceMultiplier,
        float temporalMultiplier,
        float detailMultiplier
) {
    public DirectorPerformanceState {
        requireUnitRange("heroMultiplier", heroMultiplier);
        requireUnitRange("ambientMultiplier", ambientMultiplier);
        requireUnitRange("secondaryMultiplier", secondaryMultiplier);
        requireUnitRange("distanceMultiplier", distanceMultiplier);
        requireUnitRange("temporalMultiplier", temporalMultiplier);
        requireUnitRange("detailMultiplier", detailMultiplier);
    }

    private static void requireUnitRange(String name, float value) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(name + " must be within [0,1], got " + value);
        }
    }
}