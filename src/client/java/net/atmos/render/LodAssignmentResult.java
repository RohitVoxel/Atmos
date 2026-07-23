package net.atmos.render;

/** Explainable output of LOD Assignment — Appendix ZB Blocker 6. */
public record LodAssignmentResult(
        int lodLevel,
        float lodWeight
) {}