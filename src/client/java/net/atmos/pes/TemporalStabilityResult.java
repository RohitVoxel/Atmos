package net.atmos.pes;

/** Breakdown of one Temporal Stability evaluation (§12.21). */
public record TemporalStabilityResult(
        float meanDelta,
        float value
) {}