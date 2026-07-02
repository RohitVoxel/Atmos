package net.atmos.cluster;

/**
 * Centralized, authoritative location for every Cluster Builder (Chapter 7)
 * tuning value.
 *
 * Mirrors the same pattern established by ConfidenceWeights for the
 * Confidence System (Chapter 4): no Cluster Builder class may declare its
 * own tuning constant. Every threshold used by clustering belongs here,
 * and only here.
 *
 * This is a pure relocation performed as a pre-approval cleanup pass — no
 * numerical value below differs from its original ClusterBuilder-local
 * definition, and no clustering behavior changes as a result.
 */
public final class ClusterConstants {

    private ClusterConstants() {}

    /**
     * Maximum allowed difference between a candidate cell's Tier A × Tier B
     * value and its cluster seed's value for the candidate to join.
     * Confidence values are already normalized to [0,1], so 0.15
     * represents a moderate similarity band.
     */
    public static final float SIMILARITY_THRESHOLD = 0.15f;
}