package net.atmos.pes;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cluster.Cluster;
import net.atmos.composition.Composition;

/** Shared math for PES evaluators (Chapter 12 Stage 1/2). Mirrors the ConfidenceMath/FogMath layering. */
final class PESMath {

    private PESMath() {}

    /**
     * TEMPORARY density proxy: mean Tier A x Tier B atmospheric value
     * across every cluster the Composition Engine currently retains
     * (Hero + Secondary + Ambient); 0 if none are present. Used by
     * §12.11-§12.13 only because no renderer-side density or Exposure
     * Model output exists yet. This must be replaced once Chapter 14
     * (Exposure Model) is implemented — it is not the intended permanent
     * architectural density metric, and future work must not assume it is.
     */
    static float compositionDensitySignal(Composition composition) {
        float sum = 0f;
        int count = 0;

        if (composition.heroCluster() != null) {
            sum += composition.heroCluster().averageAtmosphericValue();
            count++;
        }
        for (Cluster c : composition.secondaryClusters()) {
            sum += c.averageAtmosphericValue();
            count++;
        }
        for (Cluster c : composition.ambientClusters()) {
            sum += c.averageAtmosphericValue();
            count++;
        }

        return count == 0 ? 0f : sum / count;
    }

    /** 1.0 at zero deviation, 0.0 at/beyond {@code tolerance}. */
    static float deviationScore(float actual, float expected, float tolerance) {
        float deviation = Math.abs(actual - expected);
        return FogMath.clamp(1f - deviation / tolerance, 0f, 1f);
    }

    static boolean passesCategoryThreshold(float value) {
        return value >= PESWeights.CATEGORY_PASS_THRESHOLD;
    }

    /**
     * Single-pass delta statistics over the trailing {@code windowSize}
     * entries (oldest-first) plus {@code current}, read directly from
     * {@code history} by index — no intermediate List is materialized.
     * The only allocation is the returned record itself. Shared by
     * TemporalStabilityEvaluator (mean) and TransitionEvaluator (max) so
     * the identical window is scanned once per evaluator call rather than
     * rebuilt per statistic.
     */
    static WindowDeltaStats computeWindowDeltaStats(PESHistoryView history, PESHistoryEntry current, int windowSize) {
        int size = history.size();
        // Require at least 2 historical entries before evaluating deltas —
        // a single historical sample paired with current is not a
        // statistically meaningful trend and would make the score jump
        // the moment the second frame is ever evaluated.
        if (size < 2) {
            return new WindowDeltaStats(0f, 0f, 0);
        }

        int windowStart = Math.max(0, size - (windowSize - 1));

        float deltaSum = 0f;
        float maxDelta = 0f;
        int pairs = 0;

        PESHistoryEntry previous = history.get(windowStart);
        for (int i = windowStart + 1; i < size; i++) {
            PESHistoryEntry entry = history.get(i);
            float delta = combinedScalarDelta(previous, entry);
            deltaSum += delta;
            if (delta > maxDelta) maxDelta = delta;
            previous = entry;
            pairs++;
        }

        float finalDelta = combinedScalarDelta(previous, current);
        deltaSum += finalDelta;
        if (finalDelta > maxDelta) maxDelta = finalDelta;
        pairs++;

        return new WindowDeltaStats(deltaSum / pairs, maxDelta, pairs);
    }

    private static float combinedScalarDelta(PESHistoryEntry a, PESHistoryEntry b) {
        return (Math.abs(b.humidityMass()  - a.humidityMass())
                + Math.abs(b.stormEnergy()   - a.stormEnergy())
                + Math.abs(b.thermalEnergy() - a.thermalEnergy())
                + Math.abs(b.nightDepth()    - a.nightDepth())) / 4f;
    }

    record WindowDeltaStats(float meanDelta, float maxDelta, int pairCount) {}
}