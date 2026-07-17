package net.atmos.pes;

/**
 * Overall Perceptual Score — Chapter 12 §12.29.
 *
 * §12.29 explicitly states this combination is "processed conceptually
 * rather than as a rigid mathematical formula" — no formula or weighting
 * scheme is specified anywhere in Chapter 12 or its appendices. A
 * weighted arithmetic mean is used here, with every weight centralized
 * in PESWeights.
 *
 * Color Harmony (§12.17), Contrast (§12.18), Depth (§12.19), and
 * Exposure Evaluation (§12.25) are excluded — all require Exposure Model
 * output (Chapter 14, unbuilt). Atmospheric Rhythm / visual fatigue
 * (§12.23) is excluded — see PerceptualReport's class doc.
 *
 * Memory Evaluation (§12.24) contributes as memoryEvaluation.value(),
 * identically weighted to every other category — see MemoryEvaluator's
 * class doc for its nullable-input, neutral-fallback contract.
 *
 * Pattern Repetition contributes as a non-repetitiveness score
 * (1 - repetitionRatio when sampled) rather than its raw boolean flag,
 * so a single flip does not zero out the entire average.
 *
 * --- Motion gating (§12.26 Stage 6) ---
 *
 * Per §12.26 ("Elytra Flight -> Rapid Spatial Traversal -> Disable
 * Spatial Repetition Checks -> Shift Evaluation to Temporal Stability"),
 * when {@code motion.rapidTraversal()} is true the Pattern Repetition
 * term is neutralized to 1.0 (no penalty, no reward) instead of being
 * derived from repetitionRatio(). PatternRepetitionResult itself is
 * never altered, recomputed, or replaced — PatternRepetitionEvaluator
 * and MotionEvaluator each retain sole ownership of their own result
 * type; this evaluator only decides whether the Pattern Repetition
 * contribution is read at all.
 *
 * No additional weight is shifted onto Temporal Stability — no anchor
 * exists anywhere in §12.26 for such a multiplier, and inventing one
 * would be an unjustified coefficient. "Shift evaluation to Temporal
 * Stability" is realized as the natural consequence of Pattern
 * Repetition becoming a fixed, non-variable term during rapid travel:
 * Temporal Stability (unaffected by movement rate) remains the dominant
 * source of variance in the resulting score.
 */
public final class OverallScoreEvaluator {

    private OverallScoreEvaluator() {}

    public static float evaluate(
            EnvironmentalConsistencyResult environmentalConsistency,
            BiomeIdentityResult biomeIdentity,
            WeatherIdentityResult weatherIdentity,
            TemporalStabilityResult temporalStability,
            TransitionResult transition,
            PatternRepetitionResult patternRepetition,
            CompositionEvaluationResult compositionEvaluation,
            MotionResult motion,
            MemoryEvaluationResult memoryEvaluation) {

        float nonRepetitionScore = nonRepetitionScore(patternRepetition, motion);

        float weightedSum =
                environmentalConsistency.value() * PESWeights.OVERALL_WEIGHT_ENVIRONMENTAL_CONSISTENCY
                        + biomeIdentity.value()          * PESWeights.OVERALL_WEIGHT_BIOME_IDENTITY
                        + weatherIdentity.value()        * PESWeights.OVERALL_WEIGHT_WEATHER_IDENTITY
                        + temporalStability.value()      * PESWeights.OVERALL_WEIGHT_TEMPORAL_STABILITY
                        + transition.value()             * PESWeights.OVERALL_WEIGHT_TRANSITION
                        + nonRepetitionScore              * PESWeights.OVERALL_WEIGHT_PATTERN_NON_REPETITION
                        + compositionEvaluation.value()  * PESWeights.OVERALL_WEIGHT_COMPOSITION
                        + memoryEvaluation.value()       * PESWeights.OVERALL_WEIGHT_MEMORY;

        return weightedSum / PESWeights.OVERALL_WEIGHT_TOTAL;
    }

    private static float nonRepetitionScore(PatternRepetitionResult patternRepetition, MotionResult motion) {
        if (motion.rapidTraversal()) {
            return 1f;
        }
        return patternRepetition.sampledHeroEntries() > 0
                ? 1f - Math.min(1f, patternRepetition.repetitionRatio())
                : 1f;
    }
}