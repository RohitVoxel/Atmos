package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.composition.Composition;
import net.atmos.memory.AtmosphericMemorySnapshot;

/**
 * PES orchestration entry point — Chapter 12 Stage 1-8 scope.
 *
 * Stage 5 adds Motion Evaluation (§12.26): MotionEvaluator reads only
 * PESHistoryBuffer/Composition data already owned by this method — no new
 * external input.
 *
 * Stage 6 passes the real, unmodified PatternRepetitionResult and the real
 * MotionResult directly into OverallScoreEvaluator and
 * RecommendationEvaluator. Each consumer independently decides how to gate
 * or weight the Pattern Repetition contribution during rapid traversal
 * (§12.26). No synthetic or fabricated PatternRepetitionResult is
 * constructed anywhere in this class — PatternRepetitionEvaluator remains
 * sole owner of that type, and the published PerceptualReport.patternRepetition()
 * always reflects its true, unaltered output.
 *
 * Stage 7 adds Memory Evaluation (§12.24): the caller-supplied
 * AtmosphericMemorySnapshot is passed straight through to MemoryEvaluator,
 * which is nullable-safe — see MemoryEvaluator's class doc. No producer
 * of this snapshot is wired into AtmosClient by this task; the parameter
 * exists so a future integration can supply one without another
 * additive signature change.
 *
 * Stage 8 adds Feed-Forward Snapshot Publication (§12.31): the completed
 * PerceptualReport is published via {@link PerceptualReportManager}
 * immediately after the PESHistoryBuffer is updated, matching §12.39's
 * Step 5 (Update History) → Step 6 (Publish) ordering exactly. Publication
 * is a side effect of this method alone — no other call site may publish.
 *
 * Lifecycle order per §12.39: evaluate against the buffer's existing
 * entries, append the current frame, publish, then return.
 *
 * Not implemented: Lighting Direction Validation (§12.15),
 * visualFatigueEstimate (§12.23). This method is not invoked anywhere in
 * AtmosClient, FogManager, CompositionEngine, or AtmosphereDirector's live
 * per-frame pipeline — no such integration was authorized for this task.
 * {@link PerceptualReportManager#get()} is available for a future consumer
 * once that integration is authorized.
 */
public final class PerceptualEvaluationSystem {

    private PerceptualEvaluationSystem() {}

    /**
     * @param memory             current AtmosphericMemorySnapshot, or null
     *                           if no producer is available yet (§12.24 —
     *                           see MemoryEvaluator).
     * @param evaluationSequence caller-supplied monotonically increasing
     *                           counter — never wall-clock time; see
     *                           PESHistoryEntry's class doc.
     */
    public static PerceptualReport evaluate(EnvironmentalState env,
                                            BiomeTraits traits,
                                            Composition composition,
                                            AtmosphericMemorySnapshot memory,
                                            PESHistoryBuffer history,
                                            long evaluationSequence) {
        PESHistoryEntry current = PESHistoryEntry.capture(env, composition, evaluationSequence);

        EnvironmentalConsistencyResult environmentalConsistency =
                EnvironmentalConsistencyEvaluator.evaluate(env, composition);
        BiomeIdentityResult biomeIdentity =
                BiomeIdentityEvaluator.evaluate(traits, composition);
        WeatherIdentityResult weatherIdentity =
                WeatherIdentityEvaluator.evaluate(env, composition);
        TemporalStabilityResult temporalStability =
                TemporalStabilityEvaluator.evaluate(history, current);
        TransitionResult transition =
                TransitionEvaluator.evaluate(history, current);
        PatternRepetitionResult patternRepetition =
                PatternRepetitionEvaluator.evaluate(history, current);
        CompositionEvaluationResult compositionEvaluation =
                CompositionEvaluator.evaluate(composition);
        MotionResult motion =
                MotionEvaluator.evaluate(history, current);
        MemoryEvaluationResult memoryEvaluation =
                MemoryEvaluator.evaluate(memory, composition);

        float overallBelievabilityScore = OverallScoreEvaluator.evaluate(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition, compositionEvaluation, motion,
                memoryEvaluation);

        var recommendations = RecommendationEvaluator.evaluate(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition, compositionEvaluation, motion,
                memoryEvaluation);

        history.push(current);

        PerceptualReport report = new PerceptualReport(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition,
                compositionEvaluation, motion, memoryEvaluation, overallBelievabilityScore,
                recommendations, evaluationSequence
        );

        PerceptualReportManager.publish(report);

        return report;
    }
}