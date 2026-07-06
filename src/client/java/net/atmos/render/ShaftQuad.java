package net.atmos.render;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable, temporary render-local geometry for one crossed-quad plane
 * belonging to one procedurally expanded shaft (Chapter 9 Stage 3 —
 * Geometry Generation / "Quad Generation" per §26's Complete Rendering
 * Timeline).
 *
 * Per Appendix G §16 and Chapter 9 §24 ("Memory Management"), this is not
 * persistent simulation state and is never cached beyond the current
 * frame's rendering pass.
 *
 * v0..v3 are world-space corner positions in a fixed winding order
 * (v0 = length+/width-, v1 = length+/width+, v2 = length-/width+,
 * v3 = length-/width-), matching Chapter 9 Part 2 §9 exactly: "Every quad
 * is generated from five primary values: Center Position, Direction
 * Vector, Width, Length, Rotation Angle... the renderer computes four
 * vertices." Camera-relative conversion is intentionally NOT performed
 * here — that remains a Stage 4 responsibility requiring CameraSnapshot,
 * which this stage does not consume.
 *
 * --- Cleanup revision: `color` removed ---
 *
 * Chapter 9 §26's Complete Rendering Timeline places "Quad Generation"
 * strictly BEFORE "Texture & Color Assignment" as its own distinct
 * pipeline step. A prior revision of this class carried a RenderColor
 * field, which incorrectly pulled that later step's responsibility into
 * Stage 3. Color assignment has therefore been removed entirely from
 * this type; Stage 4 is responsible for resolving color from the source
 * RenderCluster at the appropriate point in its own pipeline.
 *
 * `brightness` is retained, but note it is NOT a Stage 3 computation —
 * it is pass-through of ShaftDescriptor.brightness(), a value already
 * fully finalized in Stage 2 (RendererExpansion.buildShaft(), per the M1
 * single-alpha-application rule). Stage 3 performs no arithmetic on it;
 * it is carried forward only so Stage 4 does not need to re-derive
 * per-shaft intensity from scratch. This does not constitute "Texture &
 * Color Assignment" work — it is inert data transport.
 */
public record ShaftQuad(
        Vec3 v0,
        Vec3 v1,
        Vec3 v2,
        Vec3 v3,
        float brightness
) {
    public ShaftQuad {
        if (v0 == null || v1 == null || v2 == null || v3 == null) {
            throw new IllegalArgumentException("quad vertices must not be null");
        }
        if (brightness < 0f || brightness > 1f) {
            throw new IllegalArgumentException("brightness must be within [0,1], got " + brightness);
        }
    }
}