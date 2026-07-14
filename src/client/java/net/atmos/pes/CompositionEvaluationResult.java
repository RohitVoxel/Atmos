package net.atmos.pes;

/**
 * Breakdown of one Composition Evaluation (§12.14).
 *
 * Each sub-score rewards variety across the compositionally significant
 * clusters (Hero + Secondary — Ambient is excluded, see
 * CompositionEvaluator). A sub-score is neutral (1f) when too few
 * clusters are present to judge variety meaningfully.
 *
 * Direction, Occlusion, and Depth (also named by §12.14) are not
 * evaluated here — they require camera-relative RenderCluster data
 * (Chapter 9) not available to Composition/Cluster at this pipeline
 * stage. Deferred, not approximated.
 */
public record CompositionEvaluationResult(
        float radiusVarietyScore,
        float intensityVarietyScore,
        float spacingVarietyScore,
        int sampledClusterCount,
        float value,
        boolean consistent
) {}