package net.atmos.aps;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free publisher for {@link OptimizationPlan} — Appendix F §4:
 * "Exactly one publisher exists: APS." Publication is package-private —
 * no ALSC decision logic exists yet to call it. Never returns null;
 * {@link OptimizationPlan#NEUTRAL} is the initial/reset state, matching
 * the failsafe already relied upon by existing consumers.
 */
public final class OptimizationPlanManager {

    private OptimizationPlanManager() {}

    private static final AtomicReference<OptimizationPlan> CURRENT =
            new AtomicReference<>(OptimizationPlan.NEUTRAL);

    static void publish(OptimizationPlan plan) {
        if (plan == null) throw new IllegalArgumentException("plan must not be null");
        CURRENT.set(plan);
    }

    public static OptimizationPlan get() {
        return CURRENT.get();
    }

    public static void reset() {
        CURRENT.set(OptimizationPlan.NEUTRAL);
    }
}