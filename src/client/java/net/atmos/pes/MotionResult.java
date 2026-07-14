package net.atmos.pes;

/** Breakdown of one Motion Evaluation (§12.26). */
public record MotionResult(
        float meanAnchorDistancePerStep,
        boolean rapidTraversal
) {}