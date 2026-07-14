package net.atmos.pes;

import java.util.EnumSet;
import java.util.Set;

/**
 * Recommendation Engine — Chapter 12 §12.32.
 *
 * Derives conceptual recommendations purely from already-computed
 * per-category pass/fail signals — no new inputs, no rendering access,
 * no write-back (§12.33, Appendix D §4). Deterministic: identical
 * category results always produce an identical recommendation set.
 */
public final class RecommendationEvaluator {

    private RecommendationEvaluator() {}

    public static Set<PerceptualRecommendation> evaluate(
            EnvironmentalConsistencyResult environmentalConsistency,
            BiomeIdentityResult biomeIdentity,
            WeatherIdentityResult weatherIdentity,
            TemporalStabilityResult temporalStability,
            TransitionResult transition,
            PatternRepetitionResult patternRepetition,
            CompositionEvaluationResult compositionEvaluation) {

        EnumSet<PerceptualRecommendation> recommendations = EnumSet.noneOf(PerceptualRecommendation.class);

        if (!environmentalConsistency.consistent() || !biomeIdentity.consistent()) {
            recommendations.add(PerceptualRecommendation.INCREASE_ENVIRONMENTAL_COHERENCE);
        }
        if (!weatherIdentity.consistent()) {
            recommendations.add(PerceptualRecommendation.ALIGN_WEATHER_HARMONY);
        }
        if (!compositionEvaluation.consistent()) {
            recommendations.add(PerceptualRecommendation.IMPROVE_COMPOSITION_BALANCE);
        }
        if (temporalStability.value() < PESWeights.CATEGORY_PASS_THRESHOLD || !transition.smooth()) {
            recommendations.add(PerceptualRecommendation.SMOOTH_TRANSITIONS);
        }
        if (patternRepetition.repetitive()) {
            recommendations.add(PerceptualRecommendation.INCREASE_VARIATION);
        }

        return recommendations;
    }
}