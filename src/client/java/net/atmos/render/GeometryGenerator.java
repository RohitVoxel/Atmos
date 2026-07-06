package net.atmos.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry Generation — Chapter 9 Stage 3 ("Quad Generation" per §26's
 * Complete Rendering Timeline).
 *
 * Converts one RenderCluster's already-expanded List<ShaftDescriptor>
 * (Stage 2 output, RendererExpansion — untouched by this cleanup pass)
 * into temporary crossed-quad geometry (Chapter 9 §8–10). Stateless and
 * deterministic.
 *
 * --- Ownership / pipeline position (ACP-001, Appendix G §2) ---
 *
 *     RenderCluster → RendererExpansion (Stage 2, approved)
 *         → List<ShaftDescriptor>
 *         → GeometryGenerator (this class — Stage 3)
 *         → ClusterGeometry / ShaftQuad
 *         → Texture & Color Assignment / GPU submission (Stage 4 — not implemented)
 *
 * Consumes only RenderCluster and List<ShaftDescriptor>. Does not access
 * CellGrid, EnvironmentalState, FogManager, CameraManager, CameraSnapshot,
 * APS, ALSC, Atmosphere Director, or Composition Engine.
 *
 * --- Deferred camera-facing billboarding ---
 *
 * Unchanged from the original Stage 3 rationale: true per-frame
 * camera-facing rotation requires CameraSnapshot, which is outside this
 * stage's consumption surface. Quads are oriented purely around the
 * shaft's own world-space axis (RenderCluster.direction()) using each
 * shaft's seeded ShaftDescriptor.rotationRadians(). Camera-facing
 * correction remains entirely a Stage 4 concern.
 *
 * --- Cleanup revision: crossed-quad count ---
 *
 * The prior revision selected crossed-quad count via
 * RenderCluster.lodLevel(), reusing RendererExpansion's Appendix G §8
 * distance-LOD table. This was an architectural error: Chapter 9 §10
 * defines crossed-quad count as a function of "Quality Level"
 * (Lowest=2 .. Ultra=6), an axis explicitly driven by the Adaptive
 * Performance System (Chapter 16) — a completely different, currently
 * unimplemented input, unrelated to Appendix G §8's distance-based LOD.
 * Conflating the two axes was incorrect.
 *
 * Per instruction, no new selection mechanism has been invented here.
 * GeometryGenerator has no valid source for "Quality Level" (APS/ALSC
 * does not exist, and Task 5 forbids reaching into it even if it did).
 * CROSSED_QUAD_COUNT is therefore fixed to Chapter 9 §10's own explicitly
 * documented "Medium" tier value (4 quads, "Smooth appearance") — an
 * architecture-cited value, not an invented one — used only as a stand-in
 * until a real Quality Level input exists.
 *
 * THIS REMAINS AN OPEN ARCHITECTURAL GAP. Real per-hardware quality
 * selection must be wired in once Chapter 16 (Adaptive Performance
 * System / ALSC) is implemented and can supply OptimizationPlan-derived
 * quality data to this stage (or, more likely, to Stage 4, which would
 * then need to inform Stage 2/3 of the desired quad density before
 * generation — an integration question for that future task, not this
 * one).
 *
 * --- Determinism ---
 *
 * Pure function of (RenderCluster, List<ShaftDescriptor>). No random
 * values, no wall-clock/tick reads, no world queries.
 *
 * --- Duplicate perpendicularBasisRight() ---
 *
 * Intentionally duplicated from RendererExpansion's private helper of
 * the same shape. Not extracted to a shared utility in this cleanup pass
 * because doing so would require modifying RendererExpansion (approved
 * Stage 2 code), which this task's instructions explicitly prohibit
 * unless required by a verified issue. No such requirement exists here.
 */
public final class GeometryGenerator {

    private GeometryGenerator() {}

    // Chapter 9 §10 "Medium" tier value, used as a fixed stand-in pending
    // a real Quality Level input from the (not yet implemented) Adaptive
    // Performance System. See class doc — this is a documented open gap,
    // not a final architectural decision.
    private static final int CROSSED_QUAD_COUNT = 4;

    private static final Vec3 REFERENCE_UP          = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 REFERENCE_UP_FALLBACK = new Vec3(1.0, 0.0, 0.0);
    private static final double PARALLEL_GUARD_DOT  = 0.99;

    public static ClusterGeometry generate(RenderCluster cluster, List<ShaftDescriptor> shafts) {
        Vec3 axis   = cluster.direction();
        Vec3 right0 = perpendicularBasisRight(axis);

        List<ShaftQuad> quads = new ArrayList<>(shafts.size() * CROSSED_QUAD_COUNT);

        for (ShaftDescriptor shaft : shafts) {
            Vec3 center = cluster.position().add(shaft.offset());
            float halfWidth  = shaft.width()  * 0.5f;
            float halfLength = shaft.length() * 0.5f;

            for (int i = 0; i < CROSSED_QUAD_COUNT; i++) {
                double angle = shaft.rotationRadians() + i * (Math.PI / CROSSED_QUAD_COUNT);
                Vec3 right = rotateAroundAxis(right0, axis, angle);

                Vec3 lengthOffset = axis.scale(halfLength);
                Vec3 widthOffset  = right.scale(halfWidth);

                Vec3 v0 = center.add(lengthOffset).subtract(widthOffset);
                Vec3 v1 = center.add(lengthOffset).add(widthOffset);
                Vec3 v2 = center.subtract(lengthOffset).add(widthOffset);
                Vec3 v3 = center.subtract(lengthOffset).subtract(widthOffset);

                quads.add(new ShaftQuad(v0, v1, v2, v3, shaft.brightness()));
            }
        }

        return new ClusterGeometry(cluster, List.copyOf(quads));
    }

    private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        Vec3 term1 = vector.scale(cos);
        Vec3 term2 = axis.cross(vector).scale(sin);
        Vec3 term3 = axis.scale(axis.dot(vector) * (1.0 - cos));

        return term1.add(term2).add(term3);
    }

    private static Vec3 perpendicularBasisRight(Vec3 direction) {
        Vec3 reference = Math.abs(direction.dot(REFERENCE_UP)) > PARALLEL_GUARD_DOT
                ? REFERENCE_UP_FALLBACK
                : REFERENCE_UP;
        return reference.cross(direction).normalize();
    }
}