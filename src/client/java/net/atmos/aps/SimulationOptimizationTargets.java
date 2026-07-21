package net.atmos.aps;

/**
 * Simulation-subsystem targets — Appendix D §2 ("SimulationOptimizationTargets:
 * Encapsulates { clusterUpdateIntervalMs, predictionRadius }"). NEUTRAL means
 * "update every call, unconstrained prediction radius," mirroring
 * MemoryCadence's existing budget=1.0 -> interval=0 convention. Owns the
 * only predictionRadius representation in this plan — no separate
 * Prediction target group exists (see OptimizationPlan class doc).
 */
public record SimulationOptimizationTargets(
        float clusterUpdateIntervalMs,
        int predictionRadius
) {
    public static final SimulationOptimizationTargets NEUTRAL =
            new SimulationOptimizationTargets(0f, Integer.MAX_VALUE);

    public SimulationOptimizationTargets {
        if (!Float.isFinite(clusterUpdateIntervalMs) || clusterUpdateIntervalMs < 0f) {
            throw new IllegalArgumentException("clusterUpdateIntervalMs must be non-negative and finite, got " + clusterUpdateIntervalMs);
        }
        if (predictionRadius < 0) {
            throw new IllegalArgumentException("predictionRadius must be non-negative, got " + predictionRadius);
        }
    }
}