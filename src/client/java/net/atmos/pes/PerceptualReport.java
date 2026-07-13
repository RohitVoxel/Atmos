package net.atmos.pes;

/**
 * PerceptualReport (Chapter 12 §12.8) — Stage 1+2 scope.
 *
 * Satisfies §12.8's temporalStabilityScore and isPatternRepetitive fields
 * via the delegating accessors below. §12.8 also names an
 * "evaluationTimestamp" field; this record exposes evaluationSequence
 * instead — see PESHistoryEntry's class doc for why (Appendix F 2.0
 * version-identifier precedent; must never be wall-clock time).
 *
 * overallBelievabilityScore, visualFatigueEstimate, and the recommendation
 * collection are omitted — they require Composition/Exposure Evaluation
 * (§12.14/§12.25, Exposure Model unbuilt), Atmospheric Rhythm Evaluation
 * (§12.23, out of this task's scope), and the Recommendation Engine
 * (§12.32, explicitly excluded). Extended additively in a future task,
 * matching SunReachResult's established precedent.
 *
 * Every Stage 1/2 category result is retained in full for explainability
 * (§12.9/§12.20), matching every other evaluator Result type in this
 * codebase.
 */
public record PerceptualReport(
        EnvironmentalConsistencyResult environmentalConsistency,
        BiomeIdentityResult biomeIdentity,
        WeatherIdentityResult weatherIdentity,
        TemporalStabilityResult temporalStability,
        TransitionResult transition,
        PatternRepetitionResult patternRepetition,
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