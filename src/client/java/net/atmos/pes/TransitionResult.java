package net.atmos.pes;

/** Breakdown of one Transition evaluation (§12.22). */
public record TransitionResult(
        float maxDelta,
        float value,
        boolean smooth
) {}