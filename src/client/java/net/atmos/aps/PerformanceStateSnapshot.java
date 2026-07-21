package net.atmos.aps;

/**
 * Immutable APS-derived performance state shape — Chapter 16 §16.9-16.10.
 * Stage 1 defines only the data shape; state derivation, hysteresis, and
 * Performance Confidence mathematics belong to a later stage.
 */
public record PerformanceStateSnapshot(
        PerformanceState state,
        PerformanceTrend trend,
        PerformanceBottleneck bottleneck
) {
    public static final PerformanceStateSnapshot NEUTRAL =
            new PerformanceStateSnapshot(PerformanceState.EXCELLENT, PerformanceTrend.STABLE, PerformanceBottleneck.NONE);

    public PerformanceStateSnapshot {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (trend == null) throw new IllegalArgumentException("trend must not be null");
        if (bottleneck == null) throw new IllegalArgumentException("bottleneck must not be null");
    }
}