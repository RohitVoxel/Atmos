package net.atmos.aps;

/**
 * Rendering-math-facing optimization targets — Appendix ZB §II
 * ("OptimizationTargets"), consumed by Appendix ZB Blocker 6 (LOD
 * Assignment — not yet implemented) and other rendering-math blockers.
 *
 * Distinct from {@link RenderingOptimizationTargets} (Appendix D §2's
 * OptimizationPlan subsystem-target group). The two are not duplicates:
 * they serve different consumers with different invariants.
 * {@code lodLimits} permits {@code 0} as an "unconstrained" sentinel
 * inside {@link OptimizationPlan}; {@code maxLodLevel} here must be
 * {@code >= 1} per Appendix ZB §III ("LOD_MAX_QUADS ... Must be >= 1").
 * {@link PerformanceSnapshotBridge} is the sole translator between the
 * two shapes — neither type reads or depends on the other.
 */
public record OptimizationTargets(
        int targetRayCount,
        int maxActiveClusters,
        int maxLodLevel
) {
    public OptimizationTargets {
        if (targetRayCount < 0) {
            throw new IllegalArgumentException(
                    "targetRayCount must be non-negative, got " + targetRayCount);
        }
        if (maxActiveClusters < 0) {
            throw new IllegalArgumentException(
                    "maxActiveClusters must be non-negative, got " + maxActiveClusters);
        }
        if (maxLodLevel < 1) {
            throw new IllegalArgumentException(
                    "maxLodLevel must be >= 1, got " + maxLodLevel);
        }
    }
}