package net.atmos.render;

/** Explainable output of Distance Evaluation — Appendix ZB Blocker 4. */
public record DistanceEvaluationResult(
        float maxRenderDistance,
        float fadeWeight
) {}