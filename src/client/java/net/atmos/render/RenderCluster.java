package net.atmos.render;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable rendering payload — the boundary contract between Atmos's
 * atmospheric composition pipeline and the ALSS Renderer, per Chapter 9
 * §4 and Appendix L (RenderCluster Contract).
 *
 * Per Appendix L §2, RenderCluster is NOT owned by the renderer. It is
 * owned by the upstream system responsible for final atmospheric
 * composition (the future Composition Engine, Chapter 10 — not yet
 * implemented). The renderer (Chapter 9, not yet implemented) is a
 * read-only consumer only.
 *
 * This type exists now, independently of both its producer and its
 * consumer, purely as the immutable data contract connecting them —
 * mirroring how SunReachResult (Chapter 8) and Cluster (Chapter 7) were
 * each defined as standalone contracts before every neighboring system
 * in their pipeline existed.
 *
 * Per Appendix L §4 (Immutability): every field is assigned exactly once
 * at construction. No field may be recomputed or mutated afterward. If
 * atmospheric conditions change, a new RenderCluster must be produced —
 * this instance is never updated in place.
 *
 * Per Appendix L §5 (Lifetime): a RenderCluster exists only for the
 * current rendering frame. It must never become part of long-term
 * simulation state, and must never be cached beyond the current frame
 * unless a future appendix explicitly permits it. No such appendix exists
 * yet, so no caching is implemented here.
 *
 * Per Appendix L §6 (Thread Ownership): construction occurs exclusively
 * on the Simulation Thread (by the future Composition Engine); consumption
 * occurs exclusively on the Render Thread (by the future ALSS Renderer).
 * This class performs no synchronization of its own — per Appendix D §11,
 * immutable data requires none for concurrent reads, which is the entire
 * reason this contract is immutable rather than a mutable shared object.
 *
 * Per Appendix L §7, the field set below is exactly the 13 fields listed
 * by Chapter 9 §4 — no more, no fewer. No renderer-internal field
 * (geometry, vertex buffers, GPU handles, quad counts) is present here;
 * those belong entirely to the renderer's own state per Appendix L §9.
 *
 * Field semantics:
 *
 *   position       — world-space position of the cluster's origin.
 *                     Per Chapter 9 §3 Principle 3, camera-relative
 *                     conversion is a renderer-side operation performed
 *                     at consumption time, not a producer-side
 *                     precondition — this field is intentionally
 *                     world-space, not camera-relative.
 *   direction       — normalized world-space orientation of the shaft.
 *   width           — shaft width in blocks.
 *   length          — shaft length in blocks.
 *   alpha           — final composed render alpha, already accounting
 *                     for confidence, SunReach, exposure, distance fade,
 *                     composition weight, and LOD scale per Chapter 9
 *                     §12 — a single final multiplier, applied exactly
 *                     once downstream (RendererExpansion), never
 *                     recomputed or reapplied elsewhere in the pipeline
 *                     (see DensityProbabilityMap's M1 correction).
 *   color           — final composed shaft color per Chapter 9 §14,
 *                     represented as the shared RenderColor type (see
 *                     RenderColor.java) rather than inline float
 *                     components or a RenderCluster-local nested type —
 *                     color is a reusable rendering concept, not
 *                     something RenderCluster should own exclusively.
 *   definition      — perceived edge sharpness per Chapter 9 §15.
 *   exposureScale   — precomputed exposure multiplier per Chapter 9 §13.
 *                     The Exposure Model (Chapter 14) that will produce
 *                     this value does not exist yet; this field only
 *                     carries whatever value its future producer supplies.
 *   fadeDistance    — distance-based fade falloff value.
 *   role            — HERO, SECONDARY, or AMBIENT, per Chapter 10's three
 *                     composition roles (not yet implemented) and the
 *                     enum shape given in Appendix A §7.3.
 *   animationPhase  — temporal shimmer/animation phase per Chapter 9 §4.
 *   lodLevel        — renderer LOD tier already assigned upstream.
 *   sunReach        — the finalized SunReach value for this cluster, per
 *                     Appendix K's combination contract. Range
 *                     [0.0, 1.10] per Appendix K §K.8.
 *
 * Validation (Appendix L §8):
 * Only the checks explicitly enumerated in Appendix L §8 are enforced
 * below. Appendix L §8 does not list a numeric range for Color,
 * Definition, Animation Phase, or LOD Level — no such range is invented
 * here. "Must already be assigned" for primitive fields (definition,
 * animationPhase, lodLevel) is trivially satisfied by any value supplied
 * to this constructor and requires no additional check.
 */
public record RenderCluster(
        Vec3 position,
        Vec3 direction,
        float width,
        float length,
        float alpha,
        RenderColor color,
        float definition,
        float exposureScale,
        float fadeDistance,
        Role role,
        float animationPhase,
        int lodLevel,
        float sunReach
) {
    // Direction normalization tolerance. Vec3 is double-backed; loose
    // enough to absorb float->double round-trip error from upstream
    // callers without masking genuine non-normalized input.
    private static final double DIRECTION_NORM_TOLERANCE = 1.0e-3;

    // SunReach upper bound per Appendix K §K.8 — 1.10 arises solely from
    // BiomeModifierResult's maximum enhancement factor (Appendix J §6).
    private static final float SUN_REACH_MAX = 1.10f;

    public RenderCluster {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (!isFinite(position)) {
            throw new IllegalArgumentException("position must be finite, got " + position);
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        if (!isFinite(direction)) {
            throw new IllegalArgumentException("direction must be finite, got " + direction);
        }

        // Finiteness is checked above before this comparison specifically
        // because Math.abs(NaN - 1.0) > tolerance evaluates to false in
        // Java — a NaN direction would otherwise silently bypass this
        // normalization check.
        double lenSq = direction.x() * direction.x()
                + direction.y() * direction.y()
                + direction.z() * direction.z();
        if (Math.abs(lenSq - 1.0) > DIRECTION_NORM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "direction must be normalized, got squared length " + lenSq);
        }

        if (width <= 0f) {
            throw new IllegalArgumentException("width must be positive, got " + width);
        }
        if (length <= 0f) {
            throw new IllegalArgumentException("length must be positive, got " + length);
        }

        // Architectural range per Chapter 9 Part 4 §27 ("Clamp all alpha
        // values: Input Alpha -> Clamp(0.0 -> 1.0) -> Render").
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("alpha must be within [0,1], got " + alpha);
        }

        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }

        // "Exposure Scale must be valid" (Appendix L §8). No numeric
        // upper bound is specified anywhere in Chapter 9 or Chapter 14
        // (Exposure Model, not yet implemented), so only the minimal
        // literal reading of "valid" is enforced: finite and
        // non-negative. This is not an invented range — it is the
        // narrowest interpretation that rejects only definitionally
        // meaningless values (NaN, Infinity, negative multipliers).
        if (!Float.isFinite(exposureScale) || exposureScale < 0f) {
            throw new IllegalArgumentException(
                    "exposureScale must be a non-negative finite value, got " + exposureScale);
        }

        if (fadeDistance < 0f) {
            throw new IllegalArgumentException(
                    "fadeDistance must be non-negative, got " + fadeDistance);
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        // "SunReach must already be finalized" (Appendix L §8) — enforced
        // against the finalized-value range Appendix K §K.8 itself
        // defines, not an invented range.
        if (sunReach < 0f || sunReach > SUN_REACH_MAX) {
            throw new IllegalArgumentException(
                    "sunReach must be within [0," + SUN_REACH_MAX + "], got " + sunReach);
        }
        // definition, animationPhase, and lodLevel: no numeric range is
        // mandated by Chapter 9 or Appendix L §8 for these fields.
        // No constraint is invented here.
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    /**
     * Composition role per Chapter 10's three roles (Hero, Secondary,
     * Ambient — Chapter 10 not yet implemented) and the enum shape given
     * in Appendix A §7.3's RenderCluster pseudocode.
     */
    public enum Role {
        HERO,
        SECONDARY,
        AMBIENT
    }
}