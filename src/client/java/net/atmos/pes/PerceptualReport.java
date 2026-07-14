package net.atmos.pes;

import java.util.Set;

/**
 * PerceptualReport (Chapter 12 §12.8) — Stage 1-4 scope.
 *
 * Stage 3 adds compositionEvaluation (§12.14). Stage 4 adds
 * overallBelievabilityScore (§12.29) and recommendations (§12.32),
 * replacing the previous Stage 1/2 report shape.
 *
 * visualFatigueEstimate (§12.8's canonical field) remains deliberately
 * omitted. Atmospheric Rhythm Evaluation (§12.23) — the section that
 * defines this estimate — requires Atmosphere Director state
 * (Chapter 11), which is not part of PES's current input contract
 * (EnvironmentalState, BiomeTraits, Composition). Wiring it in would
 * change PerceptualEvaluationSystem's public signature and was not part
 * of this task's approved Stage 3/4 scope. This is flagged as an open
 * item for a future PES task rather than approximated from unrelated
 * PES-owned signals.
 *
 * Also still deferred: Color Harmony (§12.17), Contrast (§12.18), Depth
 * (§12.19), and Exposure Evaluation (§12.25) — all require Exposure
 * Model output (Chapter 14, unbuilt). Lighting Direction Validation
 * (§12.15) and Localized Illumination (§12.16) — require camera-relative
 * RenderCluster data not available to Composition/Cluster at this
 * pipeline stage.
 */
public record PerceptualReport(
        EnvironmentalConsistencyResult environmentalConsistency,
        BiomeIdentityResult biomeIdentity,
        WeatherIdentityResult weatherIdentity,
        TemporalStabilityResult temporalStability,
        TransitionResult transition,
        PatternRepetitionResult patternRepetition,
        CompositionEvaluationResult compositionEvaluation,
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

    /** §12.8 canonical field. */
    public boolean isPatternRepetitive() {
        return patternRepetition.repetitive();
    }
}