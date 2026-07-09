package net.atmos.composition;

import net.atmos.confidence.TierCResult;

/**
 * Explainable per-cluster Confidence (Chapter 4 §4 "One Confidence Per
 * Cluster"): a Cluster's camera-independent Tier A × Tier B value combined
 * with a freshly evaluated Tier C at the cluster's center.
 *
 * Mirrors the {@code TierAResult}/{@code TierBResult}/{@code TierCResult}
 * explainability pattern.
 */
public record ClusterConfidenceResult(
        float tierABFactor,
        TierCResult tierC,
        float value
) {}