package net.atmos.render;

import net.atmos.aps.PerformanceSnapshot;

/**
 * LOD Assignment — Appendix ZB Blocker 6.
 *
 * Consumes the already-approved PerformanceSnapshotBridge output directly.
 * Does not touch OptimizationPlan, ALSC, or Chapter 16 internals — those
 * remain unimplemented and out of scope for this evaluator. Coefficients
 * sourced from RenderingMathConstants (Appendix ZB §III).
 *
 * Stateless, deterministic, O(1).
 */
public final class LodAssignmentEvaluator {

    private LodAssignmentEvaluator() {}

    public static LodAssignmentResult evaluate(float cameraDistance, PerformanceSnapshot performanceSnapshot) {
        int qualityTier = performanceSnapshot.qualityTier();

        int distanceSteps = (int) Math.floor(
                Math.max(0f, cameraDistance) / RenderingMathConstants.LOD_DISTANCE_STEP);
        int lodLevel = Math.max(1,
                RenderingMathConstants.LOD_MAX_QUADS - distanceSteps - qualityTier);
        float lodWeight = (float) lodLevel / RenderingMathConstants.LOD_MAX_QUADS;

        return new LodAssignmentResult(lodLevel, lodWeight);
    }
}