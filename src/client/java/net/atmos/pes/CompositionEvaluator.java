package net.atmos.pes;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cluster.Cluster;
import net.atmos.composition.Composition;
import net.minecraft.world.phys.Vec3;

/**
 * Composition Evaluation — Chapter 12 §12.14.
 *
 * Evaluates whether the Composition Engine's selected clusters exhibit
 * natural variety in size, intensity, and spacing, per §12.14's own
 * worked example ("Twenty identical clusters -> Low Score" vs "Different
 * widths, brightness, spacing -> High Score").
 *
 * Scope: only Hero + Secondary are sampled (bounded by
 * CompositionWeights.MAX_SECONDARY_COUNT + 1, currently 5), matching
 * Composition Engine's own Hard Rule 2-4 scope. Ambient clusters are not
 * sampled — they are explicitly background-only per Chapter 10 Part 2
 * and do not participate in composition-quality judgment.
 *
 * Zero-allocation: every metric reads directly from Composition's own
 * already-held heroCluster()/secondaryClusters() by index, computing
 * mean and variance with two bounded passes — no ArrayList, no float[],
 * no boxing. Cost is bounded by MAX_SECONDARY_COUNT+1 (currently 5,
 * so <=10 distance pairs) regardless of total candidate cluster count,
 * and never scales with world complexity.
 *
 * "Brightness" (§12.14) has no renderer-level analogue available at this
 * pipeline stage — Composition operates on Cluster, not RenderCluster
 * (see Composition's own class doc). Cluster.averageAtmosphericValue()
 * is used as the closest available proxy (the same Tier A x Tier B
 * signal PESMath.compositionDensitySignal() already reuses elsewhere in
 * this package) and is documented as an approximation, not literal
 * rendered brightness.
 *
 * Spacing uses Cluster.centerWorldPos() — absolute world-space distance,
 * not camera-relative/rendered spacing. This is correct today because
 * RenderCluster does not exist at the Composition pipeline stage (only
 * Cluster does). TODO(future Chapter 9/10 integration): once a stage
 * exposes camera-relative RenderCluster positions to Composition or PES,
 * spacing evaluation should be revisited to use rendered/screen-relative
 * spacing instead of raw world distance — two clusters far apart in
 * world space can still overlap on screen, and vice versa.
 *
 * Direction, Occlusion, and Depth (also named by §12.14) require
 * camera-relative data not available here. Deferred.
 */
public final class CompositionEvaluator {

    private CompositionEvaluator() {}

    public static CompositionEvaluationResult evaluate(Composition composition) {
        int count = significantCount(composition);

        if (count < 2) {
            return new CompositionEvaluationResult(1f, 1f, 1f, count, 1f, true);
        }

        float radiusScore    = radiusVarietyScore(composition, count);
        float intensityScore = intensityVarietyScore(composition, count);

        // Pairwise spacing variety requires >= 3 clusters. Two clusters
        // produce exactly one distance — variance of a single sample is
        // trivially zero and would unfairly penalize the common
        // Hero + one-Secondary case.
        float spacingScore = (count >= 3) ? spacingVarietyScore(composition, count) : 1f;

        float value = (radiusScore + intensityScore + spacingScore) / 3f;

        return new CompositionEvaluationResult(
                radiusScore, intensityScore, spacingScore, count,
                value, PESMath.passesCategoryThreshold(value));
    }

    private static int significantCount(Composition composition) {
        int heroCount = composition.heroCluster() != null ? 1 : 0;
        return heroCount + composition.secondaryClusters().size();
    }

    private static Cluster significantClusterAt(Composition composition, int index) {
        Cluster hero = composition.heroCluster();
        if (hero != null) {
            if (index == 0) return hero;
            return composition.secondaryClusters().get(index - 1);
        }
        return composition.secondaryClusters().get(index);
    }

    private static float radiusVarietyScore(Composition composition, int count) {
        float mean = 0f;
        for (int i = 0; i < count; i++) {
            mean += significantClusterAt(composition, i).radius();
        }
        mean /= count;

        float variance = 0f;
        for (int i = 0; i < count; i++) {
            float d = significantClusterAt(composition, i).radius() - mean;
            variance += d * d;
        }
        variance /= count;

        return varietyScoreFromMeanVariance(mean, variance, PESWeights.COMPOSITION_RADIUS_VARIETY_SATURATION);
    }

    private static float intensityVarietyScore(Composition composition, int count) {
        float mean = 0f;
        for (int i = 0; i < count; i++) {
            mean += significantClusterAt(composition, i).averageAtmosphericValue();
        }
        mean /= count;

        float variance = 0f;
        for (int i = 0; i < count; i++) {
            float d = significantClusterAt(composition, i).averageAtmosphericValue() - mean;
            variance += d * d;
        }
        variance /= count;

        return varietyScoreFromMeanVariance(mean, variance, PESWeights.COMPOSITION_INTENSITY_VARIETY_SATURATION);
    }

    private static float spacingVarietyScore(Composition composition, int count) {
        int pairCount = count * (count - 1) / 2;

        float mean = 0f;
        for (int i = 0; i < count; i++) {
            Vec3 a = significantClusterAt(composition, i).centerWorldPos();
            for (int j = i + 1; j < count; j++) {
                Vec3 b = significantClusterAt(composition, j).centerWorldPos();
                mean += (float) a.distanceTo(b);
            }
        }
        mean /= pairCount;

        float variance = 0f;
        for (int i = 0; i < count; i++) {
            Vec3 a = significantClusterAt(composition, i).centerWorldPos();
            for (int j = i + 1; j < count; j++) {
                Vec3 b = significantClusterAt(composition, j).centerWorldPos();
                float d = (float) a.distanceTo(b) - mean;
                variance += d * d;
            }
        }
        variance /= pairCount;

        return varietyScoreFromMeanVariance(mean, variance, PESWeights.COMPOSITION_SPACING_VARIETY_SATURATION);
    }

    /**
     * Coefficient-of-variation based variety score: 0 at perfect
     * uniformity, saturating to 1 at {@code saturationPoint} times the
     * mean. Implementation-defined — §12.14 gives no formula, only the
     * qualitative direction (uniform = low, varied = high).
     */
    private static float varietyScoreFromMeanVariance(float mean, float variance, float saturationPoint) {
        if (mean < 1e-4f) return 1f; // degenerate all-zero case: nothing to compare
        float coefficientOfVariation = (float) Math.sqrt(variance) / mean;
        return FogMath.clamp(coefficientOfVariation / saturationPoint, 0f, 1f);
    }
}