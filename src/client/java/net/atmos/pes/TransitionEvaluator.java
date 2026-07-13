package net.atmos.pes;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Transition evaluator — Chapter 12 §12.22.
 *
 * Flags the single worst-case consecutive delta within the trailing
 * window (same zero-allocation scan as TemporalStabilityEvaluator, via
 * PESMath.computeWindowDeltaStats), distinct from that evaluator's
 * mean-delta jitter measure (§12.21). A smoothly rising trend can have a
 * high mean delta with no abrupt single step, and vice versa — the two
 * categories catch different failure modes described by §12.21/§12.22.
 */
public final class TransitionEvaluator {

    private TransitionEvaluator() {}

    public static TransitionResult evaluate(PESHistoryView history, PESHistoryEntry current) {
        PESMath.WindowDeltaStats stats =
                PESMath.computeWindowDeltaStats(history, current, PESWeights.STABILITY_WINDOW_SIZE);

        if (stats.pairCount() == 0) {
            return new TransitionResult(0f, 1f, true);
        }

        float value = FogMath.clamp(1f - stats.maxDelta() / PESWeights.TRANSITION_ABRUPT_DELTA_THRESHOLD, 0f, 1f);
        return new TransitionResult(stats.maxDelta(), value, value >= PESWeights.TRANSITION_PASS_THRESHOLD);
    }
}