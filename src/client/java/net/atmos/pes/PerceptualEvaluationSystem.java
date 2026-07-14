package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.composition.Composition;

/**
 * PES orchestration entry point — Chapter 12 Stage 1-4 scope.
 *
 * Stage 3 adds Composition Evaluation (§12.14). Stage 4 adds the Overall
 * Perceptual Score (§12.29) and the Recommendation Engine (§12.32),
 * completing every PerceptualReport field within this task's approved
 * scope. visualFatigueEstimate remains deferred — see PerceptualReport's
 * class doc.
 *
 * Lifecycle order unchanged from §12.39: evaluate against the buffer's
 * existing entries, append the current frame, then publish. Composition
 * Evaluation and the Overall Score / Recommendations do not read
 * PESHistoryBuffer and are evaluated alongside the history-dependent
 * categories in the same pass, before the current frame is pushed.
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

        float overallBelievabilityScore = OverallScoreEvaluator.evaluate(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition, compositionEvaluation);

        var recommendations = RecommendationEvaluator.evaluate(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition, compositionEvaluation);

        history.push(current);

        return new PerceptualReport(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition,
                compositionEvaluation, overallBelievabilityScore, recommendations,
                evaluationSequence
        );
    }
}