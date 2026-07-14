package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.composition.Composition;

/**
 * PES orchestration entry point — Chapter 12 Stage 1-6 scope.
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
 * Lifecycle order unchanged from §12.39: evaluate against the buffer's
 * existing entries, append the current frame, then publish.
 *
 * Not implemented: AtomicReference-based snapshot publication (§12.31),
 * Lighting Direction Validation (§12.15), visualFatigueEstimate (§12.23).
 * Not wired into AtmosClient, FogManager, CompositionEngine, or
 * AtmosphereDirector — no such integration was authorized for this task.
 */
public final class PerceptualEvaluationSystem {

    private PerceptualEvaluationSystem() {}

    /**
     * @param evaluationSequence caller-supplied monotonically increasing
     *                           counter — never wall-clock time; see
     *                           PESHistoryEntry's class doc.
     */
    public static PerceptualReport evaluate(EnvironmentalState env,
                                            BiomeTraits traits,
                                            Composition composition,
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

        float overallBelievabilityScore = OverallScoreEvaluator.evaluate(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition, compositionEvaluation, motion);

        var recommendations = RecommendationEvaluator.evaluate(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition, compositionEvaluation, motion);

        history.push(current);

        return new PerceptualReport(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition,
                compositionEvaluation, motion, overallBelievabilityScore, recommendations,
                evaluationSequence
        );
    }
}