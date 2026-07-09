package net.atmos.composition;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cluster.Cluster;
import net.atmos.core.CameraSnapshot;

import java.util.List;

/**
 * Immutable bundle of everything a Composition Engine evaluation will
 * consume, per Chapter 10 Part 1 ("Input").
 *
 * Mirrors the identical pattern already established by
 * {@link net.atmos.confidence.ConfidenceInputs}: a flat aggregate of
 * already-existing, already-owned state. The Composition Engine reads
 * from the Cluster Builder, CameraSnapshot, and EnvironmentalState — it
 * never owns or mutates any of them (Chapter 2 §17-19, Data Ownership).
 *
 * --- Fields included in Stage 1 ---
 *
 * candidateClusters — every {@link Cluster} available for this composition
 *                      cycle, as produced by
 *                      {@link net.atmos.cluster.ClusterBuilder#build}.
 *                      Chapter 10 Part 1 ("Input"): "Candidate Clusters."
 * camera             — the current frame's {@link CameraSnapshot}, per
 *                      Chapter 10 Part 1 ("Input"): "Camera Position."
 *                      Retained as the full snapshot (not just position)
 *                      since a future scoring stage will also need look
 *                      direction and frustum for Depth/Angular-Separation
 *                      evaluation (Chapter 10 Part 3) — matching how
 *                      {@link net.atmos.confidence.TierCEvaluator} already
 *                      consumes the full snapshot rather than a bare Vec3.
 * env                — the current frame's {@link EnvironmentalState}, per
 *                      Chapter 10 Part 1 ("Input"): "Environmental State."
 *
 * --- Fields named by Chapter 10 Part 1 but deliberately NOT included ---
 *
 * Chapter 10 Part 1 lists seven input categories. The four below have no
 * existing data source anywhere in the codebase today. Per the "no
 * placeholder logic" rule, inventing a value or an empty stand-in field
 * for any of them would misrepresent unbuilt systems as available data.
 * They are omitted entirely rather than stubbed, and are documented here
 * so a future stage extends this record additively — the identical
 * precedent already used by {@link net.atmos.sunreach.SunReachResult}
 * ("Later Chapter 8 tasks will extend this record additively").
 *
 *   Confidence Scores — no per-cluster Confidence exists yet. Chapter 4
 *       §4 requires "One Confidence Per Cluster," combining Tier A x
 *       Tier B (already on Cluster, via ClusterBuilder's camera-independent
 *       similarity value) with a freshly evaluated Tier C at a
 *       representative point. Selecting that representative point is a
 *       scoring decision, explicitly deferred to the scoring stage.
 *
 *   Sun Direction — not stored as a distinct field anywhere. Only
 *       derivable via FogContext.sunAngle(), and Appendix F §3.1 assigns
 *       ownership of the authoritative solar azimuth to the SunReach
 *       layer (SunReachEvaluator), not to Composition. No SunReach output
 *       is currently threaded into the Composition pipeline.
 *
 *   Player Movement (travel direction) — only a smoothed *speed*
 *       magnitude is tracked anywhere in the codebase
 *       (FogContext.getSmoothedSpeed()). No travel-direction vector
 *       exists. Chapter 10 Part 3's "Travel Alignment" scoring explicitly
 *       requires a smoothed 3-second travel vector, which has no current
 *       owner.
 *
 *   Render Budget — owned by the Adaptive Performance System / ALSC
 *       (Chapter 16), not yet implemented.
 *
 * --- Immutability ---
 *
 * candidateClusters is defensively copied to an unmodifiable List in the
 * compact constructor, matching Cluster's own discipline for
 * memberCoords.
 */
public record CompositionInputs(
        List<Cluster> candidateClusters,
        CameraSnapshot camera,
        EnvironmentalState env
) {
    public CompositionInputs {
        if (candidateClusters == null) {
            throw new IllegalArgumentException(
                    "candidateClusters must not be null — use List.of() for none");
        }
        if (camera == null) {
            throw new IllegalArgumentException("camera must not be null");
        }
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }

        candidateClusters = List.copyOf(candidateClusters);
    }
}