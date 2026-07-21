package net.atmos.aps;

/**
 * Rendering-subsystem targets — Appendix D §2 ("RenderingOptimizationTargets:
 * Encapsulates { targetRayCount, maxActiveClusters, lodLimits }"). Field
 * types and neutral defaults are implementation-defined — Appendix D names
 * the fields but not their units — pending ALSC decision logic. NEUTRAL uses
 * Integer.MAX_VALUE to mean "unconstrained." lodLimits assumes the existing
 * RendererExpansion/RenderCluster convention where a lower index means
 * higher detail (LOD_SHAFT_COUNTS index 0 = nearest/highest detail).
 */
public record RenderingOptimizationTargets(
        int targetRayCount,
        int maxActiveClusters,
        int lodLimits
) {
    public static final RenderingOptimizationTargets NEUTRAL =
            new RenderingOptimizationTargets(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    public RenderingOptimizationTargets {
        if (targetRayCount < 0) throw new IllegalArgumentException("targetRayCount must be non-negative, got " + targetRayCount);
        if (maxActiveClusters < 0) throw new IllegalArgumentException("maxActiveClusters must be non-negative, got " + maxActiveClusters);
        if (lodLimits < 0) throw new IllegalArgumentException("lodLimits must be non-negative, got " + lodLimits);
    }
}