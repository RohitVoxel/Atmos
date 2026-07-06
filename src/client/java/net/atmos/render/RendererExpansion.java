package net.atmos.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderer Expansion — Chapter 9 Stage 2.
 *
 * Implements the deterministic procedural expansion described by
 * Appendix G (Renderer Expansion Principle) and Appendix H (Density
 * Probability Map): converts one immutable RenderCluster into a bounded,
 * temporary list of ShaftDescriptors.
 *
 * --- Ownership (Appendix G §2, ACP-001) ---
 *
 * Per ACP-001's clarified pipeline:
 *
 *     RenderCluster
 *         ↓
 *     Renderer Expansion   (this class)
 *         ↓
 *     Density Probability Map
 *         ↓
 *     Procedural Shafts
 *         ↓
 *     Billboard Geometry        (Stage 3 — NOT this class)
 *         ↓
 *     GPU Submission             (Stage 4 — NOT this class)
 *
 * This class owns only the procedural expansion step. It produces
 * ShaftDescriptor values — positions, orientations, dimensions, and
 * brightness — and stops there. It never constructs vertices, quads,
 * textures, or GPU buffers, and it never mutates the RenderCluster it
 * consumes.
 *
 * --- Statelessness (Appendix G §7, Appendix H §3) ---
 *
 * This class holds no fields and caches nothing between calls. Every
 * ShaftDescriptor produced here is temporary rendering-local data, never
 * simulation state, and is never stored beyond the returned list of one
 * expand() call.
 *
 * --- Determinism (Appendix G §6) ---
 *
 * Given an identical RenderCluster, expand() always produces an identical
 * list of ShaftDescriptors. The cluster-level seed is derived purely from
 * RenderCluster's own immutable fields (position, direction, width,
 * length, role, lodLevel) — RenderCluster carries no explicit seed field
 * (a gap between Appendix G's "Cluster Seed" language and Appendix L's
 * field list; see the implementation report's Hidden Assumption Audit).
 * Deriving the seed from already-immutable content, rather than adding a
 * new field to RenderCluster, satisfies determinism without touching the
 * Appendix L contract.
 *
 * Full frame-to-frame / camera-relative determinism (Appendix G §6 also
 * lists Camera Position and Frame Number) is intentionally out of scope —
 * CameraSnapshot is not part of this stage's consumption surface. That
 * integration belongs to Stage 4 per this project's staging.
 *
 * --- Bounded complexity (Appendix G §11-14) ---
 *
 * Shaft count per cluster is bounded by a fixed per-LOD table drawn
 * directly from Appendix G §8's own example counts (Near=250, Medium=150,
 * Far=70, Extreme=16), with a hard MAX_SHAFTS_PER_CLUSTER safety ceiling.
 * This method's complexity is O(shaftsInThisCluster), consistent with
 * Appendix G §11.
 *
 * --- Task boundary ---
 *
 * Stage 2 only. No geometry, no quads, no GPU buffers, no Composition
 * Engine, no Atmosphere Director, no APS/ALSC integration.
 */
public final class RendererExpansion {

    private RendererExpansion() {}

    // Appendix G §8 — LOD-tiered example shaft counts, reused verbatim.
    // Index 0 = nearest/highest detail, matching Chapter 9 Part 3's LOD
    // ordering. lodLevel values outside this range clamp to the nearest
    // defined tier.
    private static final int[] LOD_SHAFT_COUNTS = {250, 150, 70, 16};

    // Defensive ceiling, set above the largest documented per-cluster
    // example (250) in Appendix G §8.
    private static final int MAX_SHAFTS_PER_CLUSTER = 256;

    // Radial offset magnitude as a fraction of the cluster's own
    // width/length, so spread scales with cluster size rather than an
    // absolute constant. Appendix G §5 specifies a "Random Offset" step
    // without a magnitude; these fractions are implementation-defined.
    private static final float OFFSET_RADIUS_WIDTH_FRACTION  = 0.5f;
    private static final float OFFSET_RADIUS_LENGTH_FRACTION = 0.15f;

    private static final float DIMENSION_JITTER_FRACTION = 0.20f;
    private static final float ROTATION_JITTER_RADIANS   = 0.35f;

    // Deterministic perpendicular basis for placing offsets in world
    // space. Falls back to a secondary reference whenever direction is
    // nearly parallel to the primary reference, avoiding a degenerate
    // (near-zero-length) cross product.
    private static final Vec3 REFERENCE_UP           = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 REFERENCE_UP_FALLBACK  = new Vec3(1.0, 0.0, 0.0);
    private static final double PARALLEL_GUARD_DOT   = 0.99;

    /**
     * Deterministically expands one RenderCluster into a bounded list of
     * ShaftDescriptors. Pure function of {@code cluster}.
     */
    public static List<ShaftDescriptor> expand(RenderCluster cluster) {
        long clusterSeed = deriveClusterSeed(cluster);
        int  shaftCount  = shaftCountFor(cluster.lodLevel());

        Vec3 right = perpendicularBasisRight(cluster.direction());
        Vec3 up2   = cluster.direction().cross(right);

        List<ShaftDescriptor> shafts = new ArrayList<>(shaftCount);

        for (int i = 0; i < shaftCount; i++) {
            long sampleSeed = SeedHash.deriveSeed(clusterSeed, i);
            float densityWeight = DensityProbabilityMap.evaluate(cluster, sampleSeed);

            ShaftDescriptor shaft = buildShaft(cluster, sampleSeed, densityWeight, right, up2);

            // Appendix H §9 — omit only degenerate (non-positive) results.
            // A safety cutoff for meaningless values, not an artistic
            // quality cull.
            if (shaft.brightness() > 0f) {
                shafts.add(shaft);
            }
        }

        return List.copyOf(shafts);
    }

    private static ShaftDescriptor buildShaft(RenderCluster cluster,
                                              long sampleSeed,
                                              float densityWeight,
                                              Vec3 right,
                                              Vec3 up2) {
        long mixed = SeedHash.mix(sampleSeed);

        float angleUnit  = SeedHash.toUnitFloat(mixed);
        long  m2 = SeedHash.mix(mixed);
        float radiusUnit = SeedHash.toUnitFloat(m2);
        long  m3 = SeedHash.mix(m2);
        float widthUnit  = SeedHash.toUnitFloat(m3);
        long  m4 = SeedHash.mix(m3);
        float lengthUnit = SeedHash.toUnitFloat(m4);
        long  m5 = SeedHash.mix(m4);
        float rotUnit    = SeedHash.toUnitFloat(m5);

        float angleRadians = angleUnit * (float) (2.0 * Math.PI);
        float radiusW = cluster.width()  * OFFSET_RADIUS_WIDTH_FRACTION  * radiusUnit;
        float radiusL = cluster.length() * OFFSET_RADIUS_LENGTH_FRACTION * radiusUnit;

        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        Vec3 offset = right.scale(radiusW * cos)
                .add(up2.scale(radiusW * sin))
                .add(cluster.direction().scale(radiusL));

        float width  = cluster.width()  * jitter(widthUnit);
        float length = cluster.length() * jitter(lengthUnit);

        float rotationRadians = (rotUnit * 2f - 1f) * ROTATION_JITTER_RADIANS;

        float brightness = clamp01(cluster.alpha() * densityWeight);

        return new ShaftDescriptor(
                sampleSeed, offset, rotationRadians, width, length, brightness, densityWeight
        );
    }

    private static float jitter(float unit) {
        return 1f + (unit * 2f - 1f) * DIMENSION_JITTER_FRACTION;
    }

    private static int shaftCountFor(int lodLevel) {
        int index = Math.max(0, Math.min(lodLevel, LOD_SHAFT_COUNTS.length - 1));
        return Math.min(LOD_SHAFT_COUNTS[index], MAX_SHAFTS_PER_CLUSTER);
    }

    /**
     * Derives a stable per-cluster seed purely from RenderCluster's own
     * immutable fields. See class doc for why this exists instead of a
     * dedicated seed field on RenderCluster.
     */
    private static long deriveClusterSeed(RenderCluster cluster) {
        long h = 1125899906842597L;
        h = mixIn(h, Double.doubleToLongBits(cluster.position().x()));
        h = mixIn(h, Double.doubleToLongBits(cluster.position().y()));
        h = mixIn(h, Double.doubleToLongBits(cluster.position().z()));
        h = mixIn(h, Double.doubleToLongBits(cluster.direction().x()));
        h = mixIn(h, Double.doubleToLongBits(cluster.direction().y()));
        h = mixIn(h, Double.doubleToLongBits(cluster.direction().z()));
        h = mixIn(h, Float.floatToIntBits(cluster.width()));
        h = mixIn(h, Float.floatToIntBits(cluster.length()));
        h = mixIn(h, cluster.role().ordinal());
        h = mixIn(h, cluster.lodLevel());
        return SeedHash.mix(h);
    }

    private static long mixIn(long h, long value) {
        return 31 * h + value;
    }

    private static Vec3 perpendicularBasisRight(Vec3 direction) {
        Vec3 reference = Math.abs(direction.dot(REFERENCE_UP)) > PARALLEL_GUARD_DOT
                ? REFERENCE_UP_FALLBACK
                : REFERENCE_UP;
        return reference.cross(direction).normalize();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}