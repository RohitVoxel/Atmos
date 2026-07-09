package net.atmos.composition;

import net.atmos.cluster.Cluster;

import java.util.List;

/**
 * Immutable output contract of the Composition Engine (Chapter 10).
 *
 * Per Chapter 10 Part 1 ("Output"): "The engine returns: Hero Cluster,
 * Secondary Clusters, Ambient Clusters, Rejected Clusters. These are then
 * passed to the renderer." This record is exactly that output shape and
 * nothing more.
 *
 * --- Cluster, not RenderCluster (Stage 1 scope decision) ---
 *
 * This record classifies and transports {@link Cluster} (Chapter 7)
 * instances — the same immutable type produced by ClusterBuilder — not
 * {@link net.atmos.render.RenderCluster} (Chapter 9). Populating a
 * RenderCluster requires fields (direction, alpha, color, exposureScale,
 * sunReach, fadeDistance, animationPhase, lodLevel) that only become
 * available once scoring (Chapter 10 Part 3), sun alignment (Chapter 8 /
 * Appendix K), and exposure (Chapter 14, not yet implemented) have run.
 * All of that is explicitly out of scope for Stage 1. A later stage will
 * define the conversion from a Cluster classified here into a fully
 * populated RenderCluster once those upstream systems exist to supply the
 * missing fields — see RenderCluster's own class doc, which documents the
 * identical precedent of a data contract existing a full chapter before
 * any producer populates it.
 *
 * --- Roles (Chapter 10 Part 2) ---
 *
 * heroCluster       — the single dominant cluster for this composition, or
 *                      {@code null} if none qualifies. Chapter 10 Part 4
 *                      ("Failure Recovery") explicitly permits an absent
 *                      Hero: "Instead of forcing a Hero Shaft, Atmos
 *                      intentionally renders a quieter scene." Nullability
 *                      is therefore an architectural requirement, not an
 *                      implementation convenience.
 * secondaryClusters — clusters supporting the Hero (Chapter 10 Part 2,
 *                      "Secondary Shafts"). May be empty.
 * ambientClusters   — clusters providing background richness (Chapter 10
 *                      Part 2, "Ambient Shafts"). May be empty.
 * rejectedClusters  — candidates evaluated but not selected for any role
 *                      (Chapter 10 Part 3, "Budget Allocation": "Only the
 *                      best sixteen clusters reach the renderer. Everything
 *                      else is discarded" — discarded candidates are
 *                      retained here rather than silently dropped, matching
 *                      Chapter 10's own debug-overlay requirement that
 *                      rejection be inspectable). May be empty.
 *
 * --- What this record deliberately does NOT do (Stage 1 boundary) ---
 *
 * No constructor, factory, or builder method here selects, scores, or
 * classifies a Cluster into any of these four lists — that is Hero/
 * Secondary/Ambient/Rejection selection logic (Chapter 10 Parts 2-3),
 * explicitly deferred to a later stage. This record only defines the
 * shape those results will eventually be transported in.
 *
 * No per-cluster Confidence (Chapter 4 §4, "One Confidence Per Cluster")
 * is attached to any entry here. Producing it would require evaluating
 * Tier C at a representative point per cluster — a scoring decision
 * explicitly deferred to the future scoring stage, not invented here.
 *
 * No uniqueness constraint (a Cluster appearing in only one of the four
 * lists) is enforced in the compact constructor below. The Architecture
 * Master Guide does not specify this invariant anywhere in Chapter 10;
 * inventing one now would add an unspecified architectural rule. Flagged
 * for confirmation in the Stage 1 delivery report.
 *
 * --- Immutability & Ownership ---
 *
 * Every list is defensively copied to an unmodifiable List in the compact
 * constructor, matching the identical discipline already used by
 * {@link Cluster#memberCoords()}. Ownership of Composition construction
 * belongs exclusively to the Composition Engine (Chapter 2 §19 Data
 * Ownership: "Hero Selection | Composition Engine") — no other system may
 * construct a Composition once a future stage adds a producer.
 *
 * --- Lifetime ---
 *
 * A Composition, once a producer exists, will represent one composition
 * cycle's result (Chapter 2 §8: "Composition Engine ... Every 4-6 seconds
 * OR When travel direction changes significantly"). It is not simulation
 * history and must never be treated as long-term Atmospheric Memory
 * (Chapter 13) — a separate, not-yet-wired "Composition Memory" concept
 * (Chapter 10 Part 4) will govern retention across cycles in a later
 * stage.
 */
public record Composition(
        Cluster heroCluster,
        List<Cluster> secondaryClusters,
        List<Cluster> ambientClusters,
        List<Cluster> rejectedClusters
) {
    public Composition {
        if (secondaryClusters == null) {
            throw new IllegalArgumentException(
                    "secondaryClusters must not be null — use List.of() for none");
        }
        if (ambientClusters == null) {
            throw new IllegalArgumentException(
                    "ambientClusters must not be null — use List.of() for none");
        }
        if (rejectedClusters == null) {
            throw new IllegalArgumentException(
                    "rejectedClusters must not be null — use List.of() for none");
        }

        secondaryClusters = List.copyOf(secondaryClusters);
        ambientClusters   = List.copyOf(ambientClusters);
        rejectedClusters  = List.copyOf(rejectedClusters);

        // heroCluster is intentionally NOT null-checked — see class doc,
        // "Roles": an absent Hero is a valid, architecturally documented
        // composition state (Chapter 10 Part 4, "Failure Recovery").
    }
}