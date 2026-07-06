package net.atmos.render;

import java.util.List;

/**
 * Immutable, temporary grouping of every ShaftQuad generated from one
 * RenderCluster (Chapter 9 Stage 3 output).
 *
 * Justification (cleanup pass): Chapter 9 §19 ("Render Ordering") and
 * §26 (Complete Rendering Timeline) both require rendering to proceed
 * per-cluster, ordered by role (Hero → Secondary → Ambient). This record
 * is the minimum structure needed for Stage 4 to satisfy that ordering
 * without recomputing which quads belong to which cluster/role — it adds
 * no field beyond what §19 already requires be knowable. It is not a
 * batching, sorting, or GPU-facing structure.
 */
public record ClusterGeometry(
        RenderCluster sourceCluster,
        List<ShaftQuad> quads
) {
    public ClusterGeometry {
        if (sourceCluster == null) {
            throw new IllegalArgumentException("sourceCluster must not be null");
        }
        quads = List.copyOf(quads);
    }
}