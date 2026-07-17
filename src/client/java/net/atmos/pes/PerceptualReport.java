package net.atmos.pes;

import java.util.Set;

/**
 * PerceptualReport (Chapter 12 §12.8) — Stage 1-7 scope.
 *
 * Stage 3 added compositionEvaluation (§12.14). Stage 4 added
 * overallBelievabilityScore (§12.29) and recommendations (§12.32). Stage 5
 * added motion (§12.26) as an independently-owned diagnostic signal. Stage
 * 6 wires motion.rapidTraversal() into the Overall Score and Recommendation
 * inputs (see PerceptualEvaluationSystem) without altering the raw
 * patternRepetition field below or its §12.8 canonical accessor —
 * isPatternRepetitive() still reflects PatternRepetitionEvaluator's
 * unmodified output. §12.26's "Disable Spatial Repetition Checks" is
 * applied only to the score/recommendation inputs, never to this
 * diagnostic field.
 *
 * visualFatigueEstimate (§12.8's canonical field) remains deliberately
 * omitted. Atmospheric Rhythm Evaluation (§12.23) requires Atmosphere
 * Director state, and §12.10 ("Data Ownership") restricts PES to consuming
 * only EnvironmentalState, the Composition Engine, and the Exposure Model
 * — Atmosphere Director is not in that list, and the Feed-Forward Loop
 * (§12.31) runs PES -> Director, never Director -> PES. Chapter 11 being
 * implemented does not change this: consuming Director state here would
 * cross an explicit ownership boundary, not merely fill a missing-data gap.
 *
 * Memory Evaluation (§12.24) is now implemented — Chapter 13 (Atmospheric
 * Memory) is complete and frozen, and memoryEvaluation below reads its
 * published AtmosphericMemorySnapshot exactly as instructed. §12.10's
 * consumer list predates Chapter 13 and does not yet name it explicitly,
 * but PES's write-back prohibition ("never owns, modifies, or writes back
 * to... Atmospheric Memory") is unaffected — this field only reads the
 * immutable snapshot; see MemoryEvaluator's class doc for the full
 * reasoning and its nullable-input contract.
 *
 * Still deferred: Color Harmony (§12.17), Contrast (§12.18), Depth
 * (§12.19), and Exposure Evaluation (§12.25) — all require Exposure Model
 * output (Chapter 14, unbuilt). Lighting Direction Validation (§12.15) and
 * Localized Illumination (§12.16) — require camera-relative RenderCluster
 * data not available to Composition/Cluster at this pipeline stage.
 * Integration with Adaptive Performance (§12.27) — requires a live
 * APS/ALSC OptimizationPlan producer (Chapter 16 has no monitoring system
 * implemented) and, like §12.23, is outside §12.10's input list.
 */
public record PerceptualReport(
        EnvironmentalConsistencyResult environmentalConsistency,
        BiomeIdentityResult biomeIdentity,
        WeatherIdentityResult weatherIdentity,
        TemporalStabilityResult temporalStability,
        TransitionResult transition,
        PatternRepetitionResult patternRepetition,
        CompositionEvaluationResult compositionEvaluation,
        MotionResult motion,
        MemoryEvaluationResult memoryEvaluation,
        float overallBelievabilityScore,
        Set<PerceptualRecommendation> recommendations,
        long evaluationSequence
) {
    public PerceptualReport {
        if (environmentalConsistency == null) {
            throw new IllegalArgumentException("environmentalConsistency must not be null");
        }
        if (biomeIdentity == null) {
            throw new IllegalArgumentException("biomeIdentity must not be null");
        }
        if (weatherIdentity == null) {
            throw new IllegalArgumentException("weatherIdentity must not be null");
        }
        if (temporalStability == null) {
            throw new IllegalArgumentException("temporalStability must not be null");
        }
        if (transition == null) {
            throw new IllegalArgumentException("transition must not be null");
        }
        if (patternRepetition == null) {
            throw new IllegalArgumentException("patternRepetition must not be null");
        }
        if (compositionEvaluation == null) {
            throw new IllegalArgumentException("compositionEvaluation must not be null");
        }
        if (motion == null) {
            throw new IllegalArgumentException("motion must not be null");
        }
        if (memoryEvaluation == null) {
            throw new IllegalArgumentException("memoryEvaluation must not be null");
        }
        if (!Float.isFinite(overallBelievabilityScore)
                || overallBelievabilityScore < 0f || overallBelievabilityScore > 1f) {
            throw new IllegalArgumentException(
                    "overallBelievabilityScore must be within [0,1], got " + overallBelievabilityScore);
        }
        if (recommendations == null) {
            throw new IllegalArgumentException(
                    "recommendations must not be null — use Set.of() for none");
        }

        recommendations = Set.copyOf(recommendations);
    }

    /** §12.8 canonical field. */
    public float temporalStabilityScore() {
        return temporalStability.value();
    }

    /** §12.8 canonical field. Reflects the raw, ungated evaluator output — see class doc. */
    public boolean isPatternRepetitive() {
        return patternRepetition.repetitive();
    }

    /** True when Hero anchor traversal within the trailing window exceeds §12.26's rapid-travel threshold. */
    public boolean isRapidTraversal() {
        return motion.rapidTraversal();
    }

    /** True when current composition density remains consistent with persisted Atmospheric Memory (§12.24). */
    public boolean isMemoryConsistent() {
        return memoryEvaluation.consistent();
    }
}