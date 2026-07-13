package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.composition.Composition;

/**
 * PES orchestration entry point — Chapter 12 Stage 1+2 scope only.
 *
 * Implements §12.39's lifecycle order for the implemented categories:
 * evaluate against the buffer's existing entries, then append the
 * current frame, then return the report ("publish"). Owns the
 * PESHistoryBuffer mutation per §12.10; every evaluator invoked here
 * remains a stateless pure function of its arguments, reading history
 * only through the zero-allocation {@link PESHistoryView} interface — no
 * evaluator materializes a copy of the buffer's contents.
 *
 * Not implemented: AtomicReference-based snapshot publication (§12.31 —
 * no EnvironmentalState/Composition snapshot bus exists yet), Lighting
 * Direction Validation (§12.15 — Cluster carries no direction field),
 * overallBelievabilityScore / recommendations (see PerceptualReport).
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

        history.push(current);

        return new PerceptualReport(
                environmentalConsistency, biomeIdentity, weatherIdentity,
                temporalStability, transition, patternRepetition,
                evaluationSequence
        );
    }
}