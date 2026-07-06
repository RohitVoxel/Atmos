package net.atmos.render;

import net.minecraft.world.phys.Vec3;

/**
 * Temporary, renderer-local descriptor for one procedurally generated
 * light shaft produced by Renderer Expansion (Appendix G) from a single
 * RenderCluster, weighted by the Density Probability Map (Appendix H).
 *
 * ShaftDescriptor is NOT a simulation object. Per Appendix G §7 and
 * Appendix H §3, it exists only for the duration of one Renderer
 * Expansion pass and must never be cached, pooled across frames, or
 * treated as persistent state. It carries no geometry (no vertices, no
 * quads, no GPU data) — that responsibility belongs to Stage 3 (Geometry
 * Generation).
 *
 * Every field is derived deterministically from the parent RenderCluster's
 * own immutable content plus a per-shaft index, per Appendix G §6
 * ("Deterministic Rendering") and §8 ("Deterministic Generation").
 *
 * seed            — per-shaft derived seed, retained for debugging and
 *                    explainability (Appendix H §10) so a shaft's
 *                    parameters can be inspected without recomputing the
 *                    entire cluster expansion.
 * offset          — world-space positional offset from the parent
 *                    cluster's position (RenderCluster.position()).
 *                    Resolving this into an actual Vec3 (rather than a
 *                    polar magnitude/angle pair) is Stage 2's
 *                    responsibility because Chapter 9 Part 2 Quad
 *                    Construction expects an already-resolved "Center
 *                    Position" as input — no basis-vector resolution is
 *                    deferred to Stage 3 here; only vertex construction is.
 * rotationRadians — azimuthal orientation delta relative to the parent
 *                    cluster's direction. Interpreted by a future geometry
 *                    stage; this descriptor does not itself construct any
 *                    transform or vertex.
 * width           — this shaft's width in blocks (jittered from the
 *                    parent cluster's width).
 * length          — this shaft's length in blocks (jittered from the
 *                    parent cluster's length).
 * brightness      — final per-shaft alpha-like intensity, already
 *                    incorporating the parent cluster's composed alpha
 *                    and this shaft's Density Probability Map weight.
 *                    Clamped to [0,1].
 * densityWeight   — the raw Density Probability Map weight that produced
 *                    this shaft, retained unmultiplied for explainability
 *                    (Appendix H §10).
 */
public record ShaftDescriptor(
        long   seed,
        Vec3   offset,
        float  rotationRadians,
        float  width,
        float  length,
        float  brightness,
        float  densityWeight
) {
    public ShaftDescriptor {
        if (offset == null) {
            throw new IllegalArgumentException("offset must not be null");
        }
        if (!isFinite(offset)) {
            throw new IllegalArgumentException("offset must be finite, got " + offset);
        }
        if (!Float.isFinite(rotationRadians)) {
            throw new IllegalArgumentException("rotationRadians must be finite, got " + rotationRadians);
        }
        if (width <= 0f) {
            throw new IllegalArgumentException("width must be positive, got " + width);
        }
        if (length <= 0f) {
            throw new IllegalArgumentException("length must be positive, got " + length);
        }
        if (brightness < 0f || brightness > 1f) {
            throw new IllegalArgumentException("brightness must be within [0,1], got " + brightness);
        }
        if (!Float.isFinite(densityWeight)) {
            throw new IllegalArgumentException("densityWeight must be finite, got " + densityWeight);
        }
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }
}