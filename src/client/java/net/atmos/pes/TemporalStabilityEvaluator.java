package net.atmos.pes;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Temporal Stability evaluator — Chapter 12 §12.21.
 *
 * Mean-delta jitter measure across a trailing window (zero-allocation,
 * read directly from {@link PESHistoryView} by index via
 * PESMath.computeWindowDeltaStats) of humidityMass, stormEnergy,
 * thermalEnergy, and nightDepth — the closest available analogue to
 * §12.21's "Exposure Output" example (Exposure Model, Chapter 14, is
 * unbuilt). Distinct from TransitionEvaluator (§12.22), which flags a
 * single worst-case jump rather than average noise.
 *
 * {@code current} must not yet be pushed into {@code history} —
 * evaluation reads history before the current frame is appended, per
 * §12.39's lifecycle order.
 */
public final class TemporalStabilityEvaluator {

    private TemporalStabilityEvaluator() {}

    public static TemporalStabilityResult evaluate(PESHistoryView history, PESHistoryEntry current) {
        PESMath.WindowDeltaStats stats =
                PESMath.computeWindowDeltaStats(history, current, PESWeights.STABILITY_WINDOW_SIZE);

        if (stats.pairCount() == 0) {
            return new TemporalStabilityResult(0f, 1f);
        }

        float value = FogMath.clamp(1f - stats.meanDelta() / PESWeights.TEMPORAL_STABILITY_DELTA_TOLERANCE, 0f, 1f);
        return new TemporalStabilityResult(stats.meanDelta(), value);
    }
}