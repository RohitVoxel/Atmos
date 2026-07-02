package net.atmos.atmosphere.light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.atmos.compat.ShaderDetector;
import net.atmos.config.AtmosConfig;
import net.atmos.core.AtmosClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders crepuscular ray geometry driven by CrepuscularRayController.
 *
 * SUN-DIRECTION DECISION:
 * The sun direction vector is (−sinAngle, sunHeight, 0), which is unit-length
 * by identity (sin²+cos²=1) at zero trig cost. This places the sun path in
 * world Z=0 — an internally consistent convention that correctly tracks
 * "how high" and "dawn vs dusk side" without matching vanilla's exact azimuth.
 * Consistent with the project philosophy: translate the feeling, not exact physics.
 *
 * DYNAMIC RAY COUNT (Parts 1 + 2 of the implementation spec):
 * The system maintains a pool of MAX_RAY_COUNT=24 potential rays. Each frame,
 * only the rays whose per-ray alpha exceeds RAY_SKIP_ALPHA are actually drawn.
 * Because RAY_ALPHA_MULTS is globally sorted descending, the iteration can
 * break as soon as the threshold is crossed — all subsequent rays are weaker.
 *
 * Natural visibility scaling (confirmed by pre-flight math):
 *   intensity ≈ 0.04  →  ~4  visible rays   (barely any, clear weather)
 *   intensity ≈ 0.06  →  ~9  visible rays   (sparse trees)
 *   intensity ≈ 0.14  →  ~17 visible rays   (forest moderate)
 *   intensity ≈ 0.25  →  ~20 visible rays   (forest good dawn)
 *   intensity ≈ 0.48  →  24  visible rays   (heavy morning mist)
 *   intensity ≈ 0.55  →  24  visible rays   (peak exceptional)
 *
 * These counts are not targets — they emerge naturally from the threshold
 * mechanic without any conditional logic or special-case code.
 *
 * NON-UNIFORM DISTRIBUTION (Part 2):
 * LATERAL_OFFSETS is intentionally non-uniform and asymmetric:
 *   - A tight cluster near center (where direct sun shafts dominate)
 *   - Irregular gaps in inner and mid flanks (simulates uneven canopy/cloud)
 *   - Sparse, uneven outer rays and far outliers
 * Indices are ordered strongest-to-weakest (highest mult first) so that low
 * intensity selects only the dominant central shafts and higher intensity
 * naturally adds the flanking and outlier beams.
 *
 * PER-RAY LENGTH (Part 2):
 * RAY_LENGTH_MULTS scales each ray's length independently. Central shafts
 * punch through deeper atmosphere (longer); outer and outlier beams are
 * shorter — consistent with how real rays scatter and attenuate faster at
 * wide angles from the sun center.
 *
 * GEOMETRY:
 * Each rendered ray is a crossed quad pair (two planes at 90° around the
 * ray axis), each drawn as two concentric width layers (inner bright core
 * + wide soft envelope) via emitCrossedQuadLayered. This is the two-layer
 * feathering introduced in the visual improvement pass; it is unchanged here.
 *
 * RENDER TYPE: RenderType.lightning() — POSITION_COLOR, unlit, additive
 * translucent triangle-strip. Reused from vanilla's lightning bolt rendering.
 * All geometry shares one VertexConsumer and one draw call.
 *
 * STRIP BRIDGING: degenerate-vertex restart is handled inside emitQuad.
 * The lastVertex[] float array threads through all emitQuad calls in the
 * frame, bridging each quad's strip-start from the previous quad's last vertex.
 * Skipped rays (below RAY_SKIP_ALPHA) do not call emitQuad and therefore do
 * not affect lastVertex — the bridge between the last drawn ray and the next
 * drawn ray is always correct regardless of how many rays were skipped.
 *
 * Toggle: fog.crepuscularRays (FogConfig).
 */
public final class CrepuscularRayRenderer {

    private CrepuscularRayRenderer() {}

    // Maximum number of rays the pool can produce.
    // Actual visible count is determined per-frame by intensity × RAY_ALPHA_MULTS
    // vs RAY_SKIP_ALPHA. At typical dawn conditions only 4–17 of these are drawn.
    private static final int MAX_RAY_COUNT = 24;

    // Base ray geometry.
    private static final float RAY_BASE_LENGTH = 24f;
    private static final float NEAR_OFFSET     = 1.5f;

    // Per-ray alpha threshold. A ray whose (baseAlpha × RAY_ALPHA_MULTS[i])
    // falls below this value is not drawn. This is the mechanism that produces
    // dynamic visible count: at low intensity only the few strongest-mult rays
    // survive; at high intensity nearly all do.
    //
    // 0.012 chosen so that at intensity=0.04 exactly the top 4 mults survive.
    // At that alpha the inner feathering layer (× 0.60) would emit a vertex
    // alpha of 0.0072 — 1.8/255, genuinely invisible. Skipping is correct.
    private static final float RAY_SKIP_ALPHA = 0.012f;

    // -------------------------------------------------------------------------
    // Per-ray property arrays — all length MAX_RAY_COUNT.
    //
    // Index ordering: strongest (central cluster) → weakest (far outliers).
    // This ordering enables the break-on-threshold optimisation in renderRays().
    //
    // Lateral offset: perpendicular displacement of the ray anchor along
    // widthAxisB (roughly horizontal, perpendicular to sun direction).
    // Non-uniform distribution — intentional. Real canopy and cloud gaps
    // produce irregular spacing, not a symmetric fan.
    //
    //   Indices  0– 3: tight central cluster (dominant shafts, survive at low intensity)
    //   Indices  4– 8: inner flanks          (medium spread, irregular)
    //   Indices  9–13: mid flanks            (wider spread, sparse)
    //   Indices 14–19: outer flanks          (large spread, thin threads)
    //   Indices 20–23: far outliers          (only at peak conditions, barely visible)
    // -------------------------------------------------------------------------

    // Lateral displacement in blocks along widthAxisB (perpendicular to sun).
    // Positive = one side, negative = other side. Deliberate asymmetry —
    // real light shafts are never mirror-symmetric.
    private static final float[] LATERAL_OFFSETS = {
            // Central cluster
            0.0f, -0.6f,  1.1f, -0.2f,
            // Inner flanks
            2.0f, -1.8f,  2.7f, -2.5f,  1.5f,
            // Mid flanks
            3.4f, -3.2f,  4.0f, -4.4f,  3.0f,
            // Outer flanks
            5.2f, -5.6f,  4.7f, -6.3f,  5.8f, -4.9f,
            // Far outliers
            7.5f, -8.2f,  9.1f, -7.0f,
    };

    // Per-ray alpha multiplier applied to the global baseAlpha.
    // MUST remain globally sorted descending — the render loop uses break,
    // not continue, once the threshold is crossed.
    // Central rays are the dominant bright shafts; outliers are dim threads.
    private static final float[] RAY_ALPHA_MULTS = {
            // Central cluster
            1.00f, 0.90f, 0.85f, 0.78f,
            // Inner flanks
            0.68f, 0.63f, 0.61f, 0.57f, 0.55f,
            // Mid flanks
            0.44f, 0.42f, 0.38f, 0.36f, 0.33f,
            // Outer flanks
            0.26f, 0.24f, 0.22f, 0.20f, 0.19f, 0.17f,
            // Far outliers
            0.08f, 0.07f, 0.07f, 0.06f,
    };

    // Per-ray base width in blocks (half applied each side of the axis).
    // Central shafts are broader; outer threads are hair-thin.
    // Slight irregularity within each group — rays of identical width feel
    // mechanical; small variations keep them feeling natural.
    private static final float[] RAY_BASE_WIDTHS = {
            // Central cluster: broad shafts
            0.68f, 0.55f, 0.72f, 0.50f,
            // Inner flanks: medium
            0.42f, 0.45f, 0.38f, 0.40f, 0.36f,
            // Mid flanks: narrower
            0.30f, 0.28f, 0.25f, 0.32f, 0.22f,
            // Outer flanks: thin
            0.18f, 0.16f, 0.20f, 0.14f, 0.19f, 0.15f,
            // Far outliers: hair-thin
            0.10f, 0.12f, 0.08f, 0.11f,
    };

    // Per-ray length multiplier applied to RAY_BASE_LENGTH.
    // Central shafts punch through deeper atmosphere (longer).
    // Outer and outlier rays scatter and attenuate sooner (shorter).
    // One central ray slightly exceeds base length — that shaft catches the
    // brightest direct-sun window and extends the furthest.
    private static final float[] RAY_LENGTH_MULTS = {
            // Central cluster
            1.00f, 0.96f, 1.08f, 0.92f,
            // Inner flanks
            0.88f, 0.85f, 0.82f, 0.90f, 0.78f,
            // Mid flanks
            0.72f, 0.68f, 0.74f, 0.62f, 0.70f,
            // Outer flanks
            0.56f, 0.52f, 0.60f, 0.48f, 0.55f, 0.50f,
            // Far outliers: short
            0.38f, 0.42f, 0.35f, 0.40f,
    };

    // -------------------------------------------------------------------------
    // Width feathering layers.
    // Each ray draws four quads per-cross direction (2 axes × 2 layers):
    //   inner: narrow bright core    (width × 0.36, alpha × 0.60)
    //   outer: wide soft envelope    (width × 1.40, alpha × 0.26)
    //
    // Additive GPU blending means:
    //   Center pixel (inner + outer both overlap): 0.60 + 0.26 = 0.86 × rayAlpha
    //   Just outside inner edge (outer only):                    0.26 × rayAlpha
    //   Beyond outer edge:                                       0              → transparent
    //
    // This produces a genuine brightness gradient — bright core, soft fade —
    // without any change to emitQuad's internal logic.
    // -------------------------------------------------------------------------
    private static final float FEATHER_INNER_WIDTH_MULT = 0.36f;
    private static final float FEATHER_OUTER_WIDTH_MULT = 1.40f;
    private static final float FEATHER_INNER_ALPHA_FRAC = 0.60f;
    private static final float FEATHER_OUTER_ALPHA_FRAC = 0.26f;

    // Global alpha scale. At 0.42, peak center-ray brightness at maximum
    // intensity = 1.72 × 0.42 × 0.55 ≈ 0.397 — ~80% of the pre-feathering
    // single-layer implementation, with soft edges.
    // Tuning: if rays are too faint, increase toward 0.50.
    //         If rays are too bright, decrease toward 0.32.
    private static final float RAY_ALPHA_SCALE = 0.42f;

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!AtmosConfig.get().fog.crepuscularRays) return;
            if (!AtmosConfig.get().fog.fogEnabled) return;
            if (ShaderDetector.hasConflictingRenderer()) return;

            CrepuscularRayController rays = AtmosClient.getCrepuscularRayController();
            if (rays == null || !rays.isVisible()) return;

            renderRays(context, rays);
        });
    }

    private static void renderRays(WorldRenderContext context, CrepuscularRayController rays) {
        MultiBufferSource bufferSource = context.consumers();
        if (bufferSource == null) return;

        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) return;

        // Unit sun direction. (−sinAngle, sunHeight, 0) is unit by identity.
        Vec3 rayDir = new Vec3(-rays.sinAngle, rays.sunHeight, 0.0);

        // Crossed width axes. Cross(rayDir, WORLD_UP) is degenerate only when
        // rayDir is parallel to UP (sun at noon/midnight) — exactly when
        // horizonFactor is zero and intensity is already 0. Safe here.
        Vec3 widthAxisA = rayDir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 widthAxisB = rayDir.cross(widthAxisA).normalize();

        Matrix4f pose     = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        float baseAlpha = Math.min(1f, rays.intensity * RAY_ALPHA_SCALE);
        if (baseAlpha <= 0.002f) return;

        // Allocated once per visible frame. Threads through all emitQuad
        // calls to maintain correct degenerate-vertex strip-restart state.
        // Format: [0]=hasPrevious(0/1), [1–3]=xyz, [4–7]=rgba.
        float[] lastVertex = new float[8];

        for (int i = 0; i < MAX_RAY_COUNT; i++) {
            float rayAlpha = baseAlpha * RAY_ALPHA_MULTS[i];

            // RAY_ALPHA_MULTS is globally sorted descending.
            // First index below threshold guarantees all subsequent indices
            // are also below threshold — break is safe and avoids the rest.
            if (rayAlpha < RAY_SKIP_ALPHA) break;

            float lateral   = LATERAL_OFFSETS[i];
            float rayWidth  = RAY_BASE_WIDTHS[i];
            float rayLength = RAY_BASE_LENGTH * RAY_LENGTH_MULTS[i];

            Vec3 lateralOffset = widthAxisB.scale(lateral);
            Vec3 near = rayDir.scale(NEAR_OFFSET).add(lateralOffset);
            Vec3 far  = rayDir.scale(NEAR_OFFSET + rayLength).add(lateralOffset);

            emitCrossedQuadLayered(consumer, pose, lastVertex,
                    near, far, widthAxisA, widthAxisB,
                    rays.red, rays.green, rays.blue, rayAlpha, rayWidth);
        }
    }

    /**
     * Emits one ray as two crossed planes (A-axis and B-axis), each drawn
     * as two concentric width layers (inner core + outer envelope) for edge
     * feathering. Four emitQuad calls per ray. lastVertex is threaded through
     * all four so strip bridging is continuous across every layer boundary.
     */
    private static void emitCrossedQuadLayered(VertexConsumer consumer, Matrix4f pose, float[] lastVertex,
                                               Vec3 near, Vec3 far,
                                               Vec3 widthAxisA, Vec3 widthAxisB,
                                               float r, float g, float b,
                                               float rayAlpha, float rayWidth) {
        float innerW = rayWidth * FEATHER_INNER_WIDTH_MULT;
        float outerW = rayWidth * FEATHER_OUTER_WIDTH_MULT;
        float innerA = rayAlpha * FEATHER_INNER_ALPHA_FRAC;
        float outerA = rayAlpha * FEATHER_OUTER_ALPHA_FRAC;

        // Inner core — narrow, bright. Defines the shaft identity.
        emitQuad(consumer, pose, lastVertex, near, far, widthAxisA, r, g, b, innerA, innerW);
        emitQuad(consumer, pose, lastVertex, near, far, widthAxisB, r, g, b, innerA, innerW);
        // Outer envelope — wider, soft. Creates fading edges via additive blend.
        emitQuad(consumer, pose, lastVertex, near, far, widthAxisA, r, g, b, outerA, outerW);
        emitQuad(consumer, pose, lastVertex, near, far, widthAxisB, r, g, b, outerA, outerW);
    }

    /**
     * Emits one independent quad (4 vertices) into the shared triangle strip.
     *
     * Strip restart: if a previous quad exists (lastVertex[0] != 0), two
     * degenerate vertices are prepended — a duplicate of the previous quad's
     * final vertex (farB), then a duplicate of this quad's first vertex (nearA).
     * Every triangle formed across those degenerate vertices has a repeated
     * position and zero area, making it invisible. This "restarts" the strip
     * between independent quads without requiring a second draw call or a
     * primitive-restart index buffer.
     *
     * Skipped rays (below RAY_SKIP_ALPHA) never call this method. lastVertex
     * retains the last drawn ray's farB and the next drawn ray bridges from it
     * correctly regardless of how many rays were skipped between them.
     *
     * Alpha gradient: nearA/nearB receive full `alpha`; farA/farB receive 0.
     * Length-wise falloff — bright at camera-near, fading toward the far end.
     * Width feathering is handled one level up (emitCrossedQuadLayered) by
     * calling this method with different width and alpha arguments per layer.
     *
     * @param width full side-to-side width of this quad (half applied each side).
     */
    private static void emitQuad(VertexConsumer consumer, Matrix4f pose, float[] lastVertex,
                                 Vec3 near, Vec3 far, Vec3 widthAxis,
                                 float r, float g, float b, float alpha, float width) {
        Vec3 half  = widthAxis.scale(width * 0.5);
        Vec3 nearA = near.subtract(half);
        Vec3 nearB = near.add(half);
        Vec3 farA  = far.subtract(half);
        Vec3 farB  = far.add(half);

        if (lastVertex[0] != 0f) {
            // Degenerate vertex 1: duplicate of the previous quad's last vertex (farB).
            consumer.addVertex(pose, lastVertex[1], lastVertex[2], lastVertex[3])
                    .setColor(lastVertex[4], lastVertex[5], lastVertex[6], lastVertex[7]);
            // Degenerate vertex 2: duplicate of this quad's first vertex (nearA).
            vertex(consumer, pose, nearA, r, g, b, alpha);
        }

        vertex(consumer, pose, nearA, r, g, b, alpha);
        vertex(consumer, pose, nearB, r, g, b, alpha);
        vertex(consumer, pose, farA,  r, g, b, 0f);
        vertex(consumer, pose, farB,  r, g, b, 0f);

        lastVertex[0] = 1f;
        lastVertex[1] = (float) farB.x;
        lastVertex[2] = (float) farB.y;
        lastVertex[3] = (float) farB.z;
        lastVertex[4] = r;
        lastVertex[5] = g;
        lastVertex[6] = b;
        lastVertex[7] = 0f; // farB alpha is 0 — matches the real vertex emitted above
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 pos,
                               float r, float g, float b, float a) {
        consumer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(r, g, b, a);
    }
}