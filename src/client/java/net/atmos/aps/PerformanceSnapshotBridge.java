package net.atmos.aps;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Temporary Stage 5 Bridge — Appendix ZC §4 (Blocker 3 & 4 resolution).
 *
 * Minimal translation from {@link OptimizationPlan} (the existing
 * Chapter 16 Stage 1 contract — the only thing that actually exists
 * today) to {@link PerformanceSnapshot} (Appendix ZB §II's rendering
 * -math-facing contract). This is explicitly NOT ALSC, NOT Chapter 16,
 * and NOT an APS redesign — Chapter 16's real monitor/decision engine
 * (§16.5-16.9, Appendix D §4's hysteresis/confidence state machine) is
 * unimplemented anywhere in this codebase and remains out of scope.
 *
 * This class name and every method here are explicitly temporary. No
 * future implementation may treat anything in this file as frozen
 * architecture. It exists solely to satisfy {@link PerformanceSnapshot}'s
 * required shape until Chapter 16 / ALSC is actually implemented, at
 * which point this entire class must be replaced by ALSC's real
 * quality-tier decision output.
 *
 * --- Canonical flow (Appendix ZC §4) ---
 *
 *     ALSC -> OptimizationPlan -> OptimizationPlanManager -> PerformanceSnapshot
 *
 * {@link OptimizationPlanManager} remains the sole publisher of
 * {@link OptimizationPlan} and is not modified by this class. This
 * bridge is a stateless derivation, not a second publisher: no
 * {@code AtomicReference}, no cached state, no independent lifecycle.
 * {@link #current()} always re-derives from
 * {@link OptimizationPlanManager#get()}'s latest value at call time.
 *
 * --- qualityTier — NOT DERIVED, EXPOSED AS-IS ---
 *
 * No Chapter 16 formula exists anywhere in the Guide for converting a
 * continuous atmosphereBudget into a discrete qualityTier — that
 * conversion belongs entirely to Chapter 16 / ALSC, which does not
 * exist yet. Any formula this bridge invented to fill that gap —
 * linear scaling, nearest-anchor matching, or otherwise — would be
 * implementation-defined architecture shipped without Architect
 * approval, which is not permitted.
 *
 * The only {@link OptimizationPlan} the current architecture ever
 * actually produces is {@link OptimizationPlan#NEUTRAL}
 * (atmosphereBudget = 1.0, "no reduction requested"). Tier 0 (Ultra —
 * Chapter 16 §16.8's "Maximum Quality" profile) is the architecturally
 * correct description of that state. This bridge therefore always
 * publishes {@code qualityTier = 0}, not as a computed result of any
 * conversion algorithm, but simply because Tier 0 is the only quality
 * state the architecture currently has anything to report.
 *
 * This is not a formula and must never be extended into one. When
 * Chapter 16 / ALSC is implemented and begins publishing real
 * performance-derived budgets, this entire class must be replaced by
 * ALSC's own canonical quality-tier output — not patched with a new
 * conversion rule here.
 *
 * --- targets derivation ---
 *
 * {@link OptimizationTargets} is populated by direct field-for-field
 * reuse of {@link OptimizationPlan#renderingTargets()}. Only
 * {@code lodLimits} is floored to {@code 1} via {@code Math.max} to
 * satisfy {@link OptimizationTargets}'s stricter invariant (Appendix
 * ZB §III requires {@code LOD_MAX_QUADS >= 1}) — no other value is
 * reinterpreted, scaled, or recomputed.
 *
 * Stateless, deterministic, O(1). Simulation Thread only, per Appendix
 * D §11.
 */
public final class PerformanceSnapshotBridge {

    private PerformanceSnapshotBridge() {}

    private static final float FAILSAFE_BUDGET = 1.0f;

    /**
     * Fixed Tier 0 (Ultra) — see class doc. Not a derived value; the
     * only quality state the current architecture has anything to
     * report, since {@link OptimizationPlan#NEUTRAL} is the only
     * OptimizationPlan the architecture produces today.
     */
    private static final int TEMPORARY_FIXED_QUALITY_TIER = 0;

    /** Derives a {@link PerformanceSnapshot} from the currently published {@link OptimizationPlan}. */
    public static PerformanceSnapshot current() {
        return bridge(OptimizationPlanManager.get());
    }

    /**
     * Derives a {@link PerformanceSnapshot} from an explicit
     * {@link OptimizationPlan}; {@code null} resolves identically to
     * {@link OptimizationPlan#NEUTRAL}.
     */
    public static PerformanceSnapshot bridge(OptimizationPlan plan) {
        // resolveBudget() is retained only to preserve the null/non-finite
        // failsafe pattern (Appendix T §T.23-24) already used throughout
        // this codebase — its result is not consumed by any conversion
        // here, since none exists. See class doc.
        resolveBudget(plan);

        RenderingOptimizationTargets renderingTargets = (plan != null)
                ? plan.renderingTargets()
                : OptimizationPlan.NEUTRAL.renderingTargets();

        OptimizationTargets targets = new OptimizationTargets(
                renderingTargets.targetRayCount(),
                renderingTargets.maxActiveClusters(),
                Math.max(1, renderingTargets.lodLimits())
        );

        return new PerformanceSnapshot(TEMPORARY_FIXED_QUALITY_TIER, targets);
    }

    private static float resolveBudget(OptimizationPlan plan) {
        if (plan == null) return FAILSAFE_BUDGET;
        float raw = plan.atmosphereBudget();
        return Float.isFinite(raw) ? FogMath.clamp(raw, 0f, 1f) : FAILSAFE_BUDGET;
    }
}