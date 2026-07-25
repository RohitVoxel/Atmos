package net.atmos.diagnostics;

/**
 * Tracks the specific, factual reasons clusters/data are dropped from the pipeline.
 */
public enum RejectionReason {
    CONFIDENCE_TOO_LOW,
    BUDGET_EXCEEDED,
    EXPOSURE_TOO_LOW,
    INVALID_ALPHA,
    OCCLUDED_BY_TERRAIN,
    LOD_CULLED,
    ANGULAR_SEPARATION_VIOLATION
}