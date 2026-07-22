package net.atmos.aps;

/**
 * Immutable performance snapshot consumed by the rendering-math pipeline
 * — Appendix ZB §II ("PerformanceSnapshot"), Appendix ZC §4 (resolution
 * of the PerformanceSnapshot ownership ambiguity).
 *
 * Distinct from {@link PerformanceStateSnapshot} (Chapter 16
 * §16.9-16.10's APS-internal state/trend/bottleneck shape, unrelated to
 * this task): this record is the narrower contract Appendix ZB itself
 * defines — {@code qualityTier} plus {@link OptimizationTargets}, and
 * nothing else.
 *
 * qualityTier follows Appendix ZB Blocker 6's five-tier scale:
 * 0 = Ultra (maximum quality) through 4 = Emergency (minimum quality).
 *
 * Published exclusively via {@link PerformanceSnapshotBridge}, which
 * derives it from {@link OptimizationPlanManager}'s already-published
 * {@link OptimizationPlan} — see that class's doc for the full canonical
 * flow and why no second stateful publisher is introduced here.
 */
public record PerformanceSnapshot(
        int qualityTier,
        OptimizationTargets targets
) {
    public static final int QUALITY_TIER_MIN = 0;
    public static final int QUALITY_TIER_MAX = 4;

    public PerformanceSnapshot {
        if (qualityTier < QUALITY_TIER_MIN || qualityTier > QUALITY_TIER_MAX) {
            throw new IllegalArgumentException(
                    "qualityTier must be within [" + QUALITY_TIER_MIN + "," + QUALITY_TIER_MAX
                            + "], got " + qualityTier);
        }
        if (targets == null) {
            throw new IllegalArgumentException("targets must not be null");
        }
    }
}