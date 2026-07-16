package net.atmos.memory;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Shared update-cadence scaling math — Chapter 13 §13.18. Consumed
 * identically by {@link AtmosphericMemoryState} and
 * {@link CellMemoryIntegrator} so both channels of Atmospheric Memory
 * degrade identically under a constrained {@link OptimizationPlan}.
 *
 * Per §13.18, reduced budget lowers update *frequency* only — it never
 * reduces the mathematical precision of any single update. A null or
 * non-finite plan resolves to budget = 1.0 (§T.23–§T.24 failsafe
 * precedent, reused here), which yields interval = 0 — i.e. identical to
 * unscaled per-call advancement.
 */
final class MemoryCadence {

    private MemoryCadence() {}

    static float updateIntervalFor(OptimizationPlan plan) {
        float budget = resolveBudget(plan);
        return FogMath.lerp(
                MemoryWeights.MEMORY_UPDATE_INTERVAL_MAX_SEC,
                MemoryWeights.MEMORY_UPDATE_INTERVAL_MIN_SEC,
                budget);
    }

    private static float resolveBudget(OptimizationPlan plan) {
        if (plan == null) return 1.0f;
        float raw = plan.atmosphereBudget();
        return Float.isFinite(raw) ? FogMath.clamp(raw, 0f, 1f) : 1.0f;
    }
}