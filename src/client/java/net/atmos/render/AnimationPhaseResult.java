package net.atmos.render;

/** Explainable output of Animation Phase Producer — Appendix ZB Blocker 5. */
public record AnimationPhaseResult(
        float sigma,
        float angularSpeed,
        float animationPhase
) {}