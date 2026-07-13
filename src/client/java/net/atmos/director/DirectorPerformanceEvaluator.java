package net.atmos.director;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Adaptive Performance Budget evaluator — Chapter 11 Stage 8, Appendix T.
 *
 * Stateless and O(1) (§T.22). Consumes only
 * {@link OptimizationPlan#atmosphereBudget()} — never FPS, CPU, or GPU
 * metrics, which remain APS's exclusive responsibility (§T.2). Produces
 * six linear allocation multipliers (§T.11–§T.17) and nothing else; it
 * does not decide which visual features to disable (§T.19).
 *
 * Failure handling (§T.23–§T.24): a null plan, or a non-finite
 * atmosphereBudget, resolves to budget = 1.0 (no reduction requested).
 * Finite out-of-range values are clamped to [0,1]. No smoothing,
 * prediction, or averaging is performed (§T.25).
 */
public final class DirectorPerformanceEvaluator {

    private DirectorPerformanceEvaluator() {}

    public static DirectorPerformanceState evaluate(OptimizationPlan optimizationPlan) {
        float budget = resolveBudget(optimizationPlan);
        float heroMultiplier = Math.max(DirectorWeights.HERO_MULTIPLIER_FLOOR, budget);

        return new DirectorPerformanceState(
                heroMultiplier,
                budget,
                budget,
                budget,
                budget,
                budget
        );
    }

    private static float resolveBudget(OptimizationPlan optimizationPlan) {
        if (optimizationPlan == null) {
            return DirectorWeights.OPTIMIZATION_PLAN_FAILSAFE_BUDGET;
        }
        float raw = optimizationPlan.atmosphereBudget();
        if (!Float.isFinite(raw)) {
            return DirectorWeights.OPTIMIZATION_PLAN_FAILSAFE_BUDGET;
        }
        return FogMath.clamp(raw, 0f, 1f);
    }
}