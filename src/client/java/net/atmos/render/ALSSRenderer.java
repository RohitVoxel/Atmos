package net.atmos.render;

import net.atmos.Atmos;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.core.CameraSnapshot;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ALSS Renderer — Chapter 9 Stage 4 (GPU Submission and Rendering).
 *
 * Per ACP-001's clarified pipeline:
 *
 *   RenderCluster
 *       ↓
 *   Renderer Expansion        (Stage 2 — RendererExpansion, frozen)
 *       ↓
 *   Density Probability Map   (Stage 2 — DensityProbabilityMap, frozen)
 *       ↓
 *   Procedural Shafts         (ShaftDescriptor, frozen)
 *       ↓
 *   Geometry Generation       (Stage 3 — GeometryGenerator, frozen)
 *       ↓
 *   ClusterGeometry
 *       ↓
 *   ALSSRenderer               (this class — Stage 4)
 *       ↓
 *   GPU
 *
 * This class owns exactly what Chapter 9 assigns to the renderer and
 * nothing else: camera-relative conversion, UV assignment, texture
 * binding, final GPU-format vertex generation, transparency handling,
 * render ordering, and GPU submission. It performs no atmospheric
 * simulation, no confidence evaluation, no cluster generation, and no
 * Composition Engine responsibility (Chapter 10, not yet implemented).
 *
 * --- Stateless consumer contract ---
 *
 * This class holds no per-frame mutable state. {@link #render} is a pure
 * function of its three arguments. Every value it draws (position,
 * color, alpha, role, geometry) was already finalized upstream — per
 * Chapter 9 §3 Principle 1 ("Never Simulate"), this renderer never
 * recomputes atmospheric meaning, it only converts already-prepared data
 * into GPU-submitted geometry.
 *
 * --- Integration boundary (explicitly out of scope for this task) ---
 *
 * No Fabric render event is registered here. No {@code AtmosClient}
 * wiring exists. No temporary {@code RenderCluster}/{@code ClusterGeometry}
 * supplier is invented. {@link #render} is a public entry point awaiting
 * invocation by the future Composition Engine (Chapter 10), which will
 * supply the {@code List<ClusterGeometry>} and choose the appropriate
 * render-event phase (the existing codebase precedent for other
 * translucent world-space effects — referenced but not implemented here
 * — is a post-translucent hook, e.g. {@code WorldRenderEvents.AFTER_TRANSLUCENT}).
 * Neither of those decisions is made by this class.
 *
 * --- RenderType choice (implementation-defined, documented) ---
 *
 * Uses vanilla {@link RenderType#beaconBeam(ResourceLocation, boolean)}
 * with {@code translucent = true}, reused rather than a custom composite
 * state, per Chapter 9 §6 ("Reuse existing rendering infrastructure
 * whenever possible") and the explicit instruction to use standard
 * alpha-based translucency rather than additive blending. This RenderType
 * already provides:
 *
 *   - standard SRC_ALPHA / ONE_MINUS_SRC_ALPHA translucency
 *   - a POSITION_COLOR_TEX vertex format, matching our neutral-grayscale,
 *     alpha-falloff shaft textures exactly (RGB tint comes entirely from
 *     the vertex color / RenderCluster.color(), never from the texture)
 *   - double-sided (non-culled) quads
 *
 * That last property is this task's resolution of "camera-facing
 * rendering": Stage 3's crossed-quad geometry (RendererExpansion,
 * GeometryGenerator — frozen) already provides multi-angle volumetric
 * coverage via several quads at fixed rotational offsets around the
 * shaft's own axis. Re-orienting each quad to fully face the camera here
 * would collapse that crossed structure onto a single plane, destroying
 * Stage 3's design. Ensuring every quad renders regardless of which side
 * the camera approaches from (i.e. never culled) is the correct, minimal,
 * non-invented reading of Chapter 9 §9's "quads always face the camera"
 * given Stage 3 is frozen.
 *
 * --- Camera-relative conversion ---
 *
 * Per Chapter 9 §3 Principle 3, conversion is direct Vec3 subtraction
 * (worldPosition - camera.position()), never PoseStack matrix
 * multiplication. This matches CameraSnapshot's own class doc, which
 * explicitly anticipates a renderer not needing a pose-stack transform at
 * all and fetching one itself at its own render-time hook if it ever did
 * — this renderer does not.
 *
 * --- Texture variant selection ---
 *
 * Neither RenderCluster nor ClusterGeometry nor ShaftQuad carries a
 * texture reference or a retained per-shaft seed (ShaftQuad deliberately
 * omits ShaftDescriptor.seed() — see ShaftQuad's own class doc). Adding
 * such a field would require modifying frozen Stage 2/3 contracts, which
 * this task does not authorize. Instead, a deterministic hash of each
 * quad's own already-fixed world-space v0 vertex (reusing the
 * package-private {@link SeedHash} utility already established in this
 * package for exactly this purpose) selects among the three supplied
 * shaft textures. This is procedural visual variety per Appendix H §5.6
 * ("Noise shall only introduce visual variation, never determine
 * atmospheric behavior") — not a new simulation input.
 *
 * --- Alpha / color application (single-application rule) ---
 *
 * ShaftQuad.brightness() is ALREADY the fully composed final alpha
 * (cluster.alpha() x density weight, applied exactly once in Stage 2 —
 * see RendererExpansion.buildShaft() and DensityProbabilityMap's M1
 * correction). This class applies it exactly once more here as the
 * vertex alpha channel and never re-reads RenderCluster.alpha() or
 * RenderCluster.exposureScale() directly — doing so would reproduce the
 * exact double-application defect already caught and fixed upstream.
 * RGB comes directly from RenderCluster.color() (uniform per cluster —
 * Chapter 9 §14's described vertical color gradient is not implemented
 * because no upstream field carries per-vertex gradient stops; adding
 * one would be new, unauthorized data, not a rendering step).
 *
 * --- Render ordering ---
 *
 * Per Chapter 9 §19: Hero -> Secondary -> Ambient, farthest-to-nearest
 * within each role. Implemented as an explicit sort over the supplied
 * ClusterGeometry list before any GPU submission occurs.
 *
 * --- Deliberately NOT implemented (out of scope for this task) ---
 *
 *   - Distance/frustum culling (Chapter 9 §17-18) — belongs to a future
 *     Adaptive Performance integration; not requested by this task.
 *   - LOD-based quad density (RenderCluster.lodLevel()) — GeometryGenerator
 *     (frozen, Stage 3) already documents this as an open gap awaiting
 *     Chapter 16; not re-addressed here.
 *   - definition / fadeDistance / animationPhase — no Stage 4 transfer
 *     function is specified anywhere in Chapter 9 for these fields;
 *     inventing one would violate scope discipline. Logged as deferred.
 *   - Vertical per-shaft color gradient (Chapter 9 §14) — no upstream
 *     field carries gradient stops.
 */
public final class ALSSRenderer {

    private ALSSRenderer() {}

    // Resource paths exactly as supplied. Namespace is Atmos.MOD_ID
    // ("atmos", lowercase) — the corrected textures must be placed at
    // resources/assets/atmos/textures/shafts/ (the supplied zip's folder
    // used "Atmos" with a capital A, which is invalid as a Minecraft
    // ResourceLocation namespace and must be corrected at asset-placement
    // time; no pixel data changes).
    private static final String[] SHAFT_TEXTURE_PATHS = {
            "textures/shafts/light_shaft_01.png",
            "textures/shafts/light_shaft_02.png",
            "textures/shafts/light_shafts_03.png"
    };

    // Built once, lazily, on first class reference — well after the
    // render system is initialized, since nothing in the codebase
    // references this class yet (no render event is registered by this
    // task). RenderType.beaconBeam(...) mirrors vanilla's own static
    // RenderType construction pattern and requires no active GL context
    // to construct, only to eventually bind.
    private static final RenderType[] SHAFT_RENDER_TYPES = buildRenderTypes();

    private static RenderType[] buildRenderTypes() {
        RenderType[] types = new RenderType[SHAFT_TEXTURE_PATHS.length];
        for (int i = 0; i < SHAFT_TEXTURE_PATHS.length; i++) {
            ResourceLocation texture =
                    ResourceLocation.fromNamespaceAndPath(Atmos.MOD_ID, SHAFT_TEXTURE_PATHS[i]);
            types[i] = RenderType.beaconBeam(texture, true);
        }
        return types;
    }

    /**
     * Renders every quad in {@code clusterGeometries} into
     * {@code bufferSource}, camera-relative to {@code camera}.
     *
     * Pure function of its arguments. Safe to call with an empty or null
     * geometry list (no-op). Performs its own GPU submission —
     * {@code bufferSource.endBatch(...)} is called internally for every
     * RenderType actually used; callers do not need to flush afterward
     * for these specific RenderTypes.
     *
     * @param bufferSource      the current frame's buffer source. Must be
     *                          a {@code BufferSource} (not the narrower
     *                          {@code MultiBufferSource} interface)
     *                          because this renderer flushes its own
     *                          batches rather than relying on a caller to
     *                          do so — appropriate for an immediate-style
     *                          translucent effect that is not part of
     *                          vanilla's regular deferred chunk layers.
     * @param camera            current frame's CameraSnapshot, used only
     *                          for its {@code position()} (camera-relative
     *                          conversion) and, for render ordering,
     *                          distance-to-cluster comparisons.
     * @param clusterGeometries Stage 3 output for every cluster to be
     *                          drawn this frame. Never mutated.
     */
    public static void render(MultiBufferSource.BufferSource bufferSource,
                              CameraSnapshot camera,
                              List<ClusterGeometry> clusterGeometries) {
        if (bufferSource == null || camera == null) return;
        if (clusterGeometries == null || clusterGeometries.isEmpty()) return;

        Vec3 cameraPos = camera.position();

        List<ClusterGeometry> ordered = orderForRendering(clusterGeometries, cameraPos);

        boolean[] textureUsed = new boolean[SHAFT_RENDER_TYPES.length];

        for (ClusterGeometry geometry : ordered) {
            RenderCluster cluster = geometry.sourceCluster();
            RenderColor color = cluster.color();

            for (ShaftQuad quad : geometry.quads()) {
                int textureIndex = textureIndexFor(quad);
                RenderType renderType = SHAFT_RENDER_TYPES[textureIndex];
                VertexConsumer consumer = bufferSource.getBuffer(renderType);

                submitQuad(consumer, cameraPos, quad, color);
                textureUsed[textureIndex] = true;
            }
        }

        for (int i = 0; i < SHAFT_RENDER_TYPES.length; i++) {
            if (textureUsed[i]) {
                bufferSource.endBatch(SHAFT_RENDER_TYPES[i]);
            }
        }
    }

    /**
     * Chapter 9 §19 Render Ordering: Hero -&gt; Secondary -&gt; Ambient,
     * farthest-to-nearest within each role. A defensive copy is sorted;
     * the caller's list is never mutated.
     */
    private static List<ClusterGeometry> orderForRendering(List<ClusterGeometry> input, Vec3 cameraPos) {
        List<ClusterGeometry> ordered = new ArrayList<>(input);
        ordered.sort(
                Comparator
                        .comparingInt((ClusterGeometry g) -> rolePriority(g.sourceCluster().role()))
                        .thenComparing(
                                (ClusterGeometry g) -> distanceSquared(cameraPos, g.sourceCluster().position()),
                                Comparator.reverseOrder()
                        )
        );
        return ordered;
    }

    private static int rolePriority(RenderCluster.Role role) {
        return switch (role) {
            case HERO -> 0;
            case SECONDARY -> 1;
            case AMBIENT -> 2;
        };
    }

    private static double distanceSquared(Vec3 a, Vec3 b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Deterministic texture-variant selection. Reuses the package-private
     * {@link SeedHash} utility already established for Stage 2 procedural
     * variety — hashes the quad's own fixed, already-deterministic world
     * -space v0 vertex rather than inventing a new seed field on a frozen
     * Stage 3 type. See class doc "Texture variant selection."
     */
    private static int textureIndexFor(ShaftQuad quad) {
        Vec3 v0 = quad.v0();
        long h = Double.doubleToLongBits(v0.x());
        h = 31 * h + Double.doubleToLongBits(v0.y());
        h = 31 * h + Double.doubleToLongBits(v0.z());

        long mixed = SeedHash.mix(h);
        float unit = SeedHash.toUnitFloat(mixed);

        int index = (int) (unit * SHAFT_RENDER_TYPES.length);
        return Math.min(SHAFT_RENDER_TYPES.length - 1, Math.max(0, index));
    }

    /**
     * Submits one quad's four vertices in ShaftQuad's fixed documented
     * winding order (v0=length+/width-, v1=length+/width+,
     * v2=length-/width+, v3=length-/width-), with UV assigned to match:
     * v0->(0,1), v1->(1,1), v2->(1,0), v3->(0,0).
     *
     * Color is RenderCluster.color(), applied uniformly across all four
     * vertices (see class doc — no gradient data is available upstream).
     * Alpha is quad.brightness() — the single, already-final composed
     * value; see class doc "Alpha / color application."
     */
    private static void submitQuad(VertexConsumer consumer, Vec3 cameraPos, ShaftQuad quad, RenderColor color) {
        int r = colorChannel(color.red());
        int g = colorChannel(color.green());
        int b = colorChannel(color.blue());
        int a = colorChannel(quad.brightness());

        addVertex(consumer, cameraPos, quad.v0(), 0f, 1f, r, g, b, a);
        addVertex(consumer, cameraPos, quad.v1(), 1f, 1f, r, g, b, a);
        addVertex(consumer, cameraPos, quad.v2(), 1f, 0f, r, g, b, a);
        addVertex(consumer, cameraPos, quad.v3(), 0f, 0f, r, g, b, a);
    }

    /**
     * Camera-relative conversion via direct Vec3 subtraction (Chapter 9
     * §3 Principle 3), converted to float only at this final step to
     * preserve double precision through every prior stage.
     */
    private static void addVertex(VertexConsumer consumer, Vec3 cameraPos, Vec3 worldPos,
                                  float u, float v, int r, int g, int b, int a) {
        float x = (float) (worldPos.x() - cameraPos.x());
        float y = (float) (worldPos.y() - cameraPos.y());
        float z = (float) (worldPos.z() - cameraPos.z());

        consumer.addVertex(x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setUv2(0xF000F0, 0xF000F0) // Default full light / max brightness
                .setNormal(0.0f, 1.0f, 0.0f); // Default up-vector normal
    }

    private static int colorChannel(float value) {
        return Math.round(FogMath.clamp(value, 0f, 1f) * 255f);
    }
}