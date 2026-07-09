package net.atmos.composition;

import net.atmos.cluster.Cluster;
import net.atmos.confidence.TierCEvaluator;
import net.atmos.confidence.TierCResult;
import net.atmos.core.CameraSnapshot;

/**
 * Per-cluster Confidence evaluator, completing the "One Confidence Per
 * Cluster" requirement of Chapter 4 §4 that {@link
 * net.atmos.cluster.ClusterBuilder} explicitly defers (it produces only the
 * camera-independent Tier A × Tier B value, stored as {@link
 * Cluster#averageAtmosphericValue()}) and that {@link CompositionInputs}
 * names as "explicitly deferred to the scoring stage."
 *
 * The representative point for Tier C evaluation is {@link
 * Cluster#centerWorldPos()} — the only camera-relative point already owned
 * by Cluster, requiring no invented data.
 *
 * Combination is straight multiplication of Tier A×B and Tier C, per
 * Chapter 4 §3 / Appendix B §3 — the same top-level rule {@link
 * net.atmos.confidence.ConfidenceSystem} uses, not {@code ConfidenceMath}'s
 * within-tier weighted geometric product.
 */
public final class ClusterConfidenceEvaluator {

    private ClusterConfidenceEvaluator() {}

    public static ClusterConfidenceResult evaluate(Cluster cluster, CameraSnapshot camera) {
        float tierABFactor = cluster.averageAtmosphericValue();
        TierCResult tierC = TierCEvaluator.evaluate(camera, cluster.centerWorldPos());

        float value = tierABFactor * tierC.value();

        return new ClusterConfidenceResult(tierABFactor, tierC, value);
    }
}