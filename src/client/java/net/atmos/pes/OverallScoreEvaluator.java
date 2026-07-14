package net.atmos.pes;

/**
 * Overall Perceptual Score — Chapter 12 §12.29.
 *
 * §12.29 explicitly states this combination is "processed conceptually
 * rather than as a rigid mathematical formula" — no formula or weighting
 * scheme is specified anywhere in Chapter 12 or its appendices. A
 * weighted arithmetic mean is used here, with every weight centralized
 * in PESWeights (mirroring ConfidenceWeights / DirectorWeights /
 * CompositionWeights) rather than hardcoded in this class. All weights
 * are currently equal, which reproduces a plain arithmetic mean exactly
 * — this is a future-proofing change, not a behavioral one.
 *
 * Color Harmony (§12.17), Contrast (§12.18), Depth (§12.19), and
 * Exposure Evaluation (§12.25) are excluded — all require Exposure Model
 * output (Chapter 14, unbuilt). Atmospheric Rhythm / visual fatigue
 * (§12.23) is excluded — see PerceptualReport's class doc.
 *
 * Pattern Repetition contributes as a non-repetitiveness score
 * (1 - repetitionRatio when sampled) rather than its raw boolean flag,
 * so a single flip does not zero out the entire average.
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
            CompositionEvaluationResult compositionEvaluation) {

        float nonRepetitionScore = patternRepetition.sampledHeroEntries() > 0
                ? 1f - Math.min(1f, patternRepetition.repetitionRatio())
                : 1f;

        float weightedSum =
                environmentalConsistency.value() * PESWeights.OVERALL_WEIGHT_ENVIRONMENTAL_CONSISTENCY
                        + biomeIdentity.value()          * PESWeights.OVERALL_WEIGHT_BIOME_IDENTITY
                        + weatherIdentity.value()        * PESWeights.OVERALL_WEIGHT_WEATHER_IDENTITY
                        + temporalStability.value()      * PESWeights.OVERALL_WEIGHT_TEMPORAL_STABILITY
                        + transition.value()             * PESWeights.OVERALL_WEIGHT_TRANSITION
                        + nonRepetitionScore              * PESWeights.OVERALL_WEIGHT_PATTERN_NON_REPETITION
                        + compositionEvaluation.value()  * PESWeights.OVERALL_WEIGHT_COMPOSITION;

        return weightedSum / PESWeights.OVERALL_WEIGHT_TOTAL;
    }
}