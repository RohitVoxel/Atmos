package net.atmos.aps;

/**
 * Immutable, hierarchical performance budget — Appendix D §2 (canonical
 * OptimizationPlan architecture). atmosphereBudget is the frozen Stage 8
 * global field (Appendix T §T.4) consumed by DirectorPerformanceEvaluator
 * and MemoryCadence; deliberately left unvalidated here per Appendix T
 * §T.7/§T.23-24 — clamping/failsafe remains the consumer's responsibility.
 * The three named subsystem target groups are Appendix D §2's exact
 * "Extensible Examples"; additional groups may be appended later without
 * breaking existing consumers, since those read only atmosphereBudget().
 */
public record OptimizationPlan(
        float atmosphereBudget,
        RenderingOptimizationTargets renderingTargets,
        SimulationOptimizationTargets simulationTargets,
        MemoryOptimizationTargets memoryTargets
) {
    public static final OptimizationPlan NEUTRAL = new OptimizationPlan(1.0f);

    public OptimizationPlan {
        if (renderingTargets == null) throw new IllegalArgumentException("renderingTargets must not be null");
        if (simulationTargets == null) throw new IllegalArgumentException("simulationTargets must not be null");
        if (memoryTargets == null) throw new IllegalArgumentException("memoryTargets must not be null");
        // atmosphereBudget deliberately unvalidated — see class doc.
    }

    /** Preserves the original single-field construction shape (Stage 8 contract). */
    public OptimizationPlan(float atmosphereBudget) {
        this(atmosphereBudget, RenderingOptimizationTargets.NEUTRAL,
                SimulationOptimizationTargets.NEUTRAL, MemoryOptimizationTargets.NEUTRAL);
    }
}