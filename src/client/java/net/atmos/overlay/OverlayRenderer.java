package net.atmos.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.atmos.config.AtmosConfig;
import net.atmos.config.AtmosReloadable;
import net.atmos.core.AtmosClient;
import net.atmos.render.gpu.OverlayGpuCache;
import net.atmos.render.gpu.OverlayGpuMesh;
import net.atmos.render.gpu.VisibilityManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Pure consumer — Batch 3 §11. Draws only what OverlayGpuCache retains,
 * invokes its budgeted invalidation drain (the render thread is the only
 * valid GPU-upload context), performs frustum culling. Nothing else.
 *
 * World-space fix (Batch 3 Phase 11): this class's camera-relative
 * transform was already mathematically correct (full X/Y/Z translate) and
 * is unchanged. See CloudRenderer for the actual defect and fix — that
 * renderer only translated Y, leaving X/Z uncompensated, which is the
 * root cause of "stuck to camera" behavior. Both renderers now share the
 * identical transform pattern.
 *
 * Performance (Phase 10): the model-view matrix is identical for every
 * mesh drawn this frame (depends only on the pose stack and camera
 * position, both frame-constant) — hoisted out of the per-mesh loop
 * instead of being reallocated per mesh. activeType tracking uses a
 * primitive bitmask instead of an EnumSet to avoid a per-frame heap
 * allocation.
 */
public final class OverlayRenderer implements AtmosReloadable {

    private final OverlayManager overlayManager;
    private final OverlaySurfaceProvider surfaceProvider;
    private final OverlaySurfaceStateStore stateStore;
    private final OverlayInvalidationQueue invalidationQueue;
    private final OverlayGpuCache gpuCache = new OverlayGpuCache();

    private boolean hasRendered = false;
    private long lastRenderNanos = 0L;
    private int lastActiveLayerCount = 0;
    private int lastRenderedFaces = 0;

    public OverlayRenderer(OverlayManager overlayManager, OverlaySurfaceProvider surfaceProvider,
                           OverlaySurfaceStateStore stateStore, OverlayInvalidationQueue invalidationQueue) {
        this.overlayManager = overlayManager;
        this.surfaceProvider = surfaceProvider;
        this.stateStore = stateStore;
        this.invalidationQueue = invalidationQueue;
    }

    public void render(WorldRenderContext context) {
        long start = System.nanoTime();
        int renderedFaces = 0;
        int activeTypeMask = 0;

        Minecraft mc = Minecraft.getInstance();
        if (!AtmosConfig.get().overlay.overlaysEnabled || !AtmosConfig.get().overlay.surfaceRendererEnabled
                || mc.level == null || context.matrixStack() == null) {
            finish(start, renderedFaces, 0);
            return;
        }

        ClientLevel level = mc.level;
        long currentTick = AtmosClient.getTickScheduler().currentTick();
        float depthOffset = AtmosConfig.get().overlay.safeSurfaceDepthOffset();
        long budgetNanos = AtmosConfig.get().overlay.safeOverlayGpuRebuildBudgetNanos();

        gpuCache.drainInvalidations(invalidationQueue, surfaceProvider, stateStore, level, depthOffset, currentTick, budgetNanos);

        Vec3 cameraPos = context.camera().getPosition();
        Matrix4f projection = context.projectionMatrix();
        Frustum frustum = context.frustum();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // Camera-relative transform — identical for every mesh this frame.
        Matrix4f modelView = new Matrix4f(context.matrixStack().last().pose())
                .translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, OverlayUniversalTexture.MASK);
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);
        var shader = RenderSystem.getShader();

        boolean seasonalEnabled = AtmosConfig.get().overlay.seasonalOverlaysEnabled;
        boolean rainEnabled = AtmosConfig.get().overlay.rainOverlaysEnabled;

        for (var entry : gpuCache.allMeshEntries()) {
            InvalidationKey key = entry.getKey();
            OverlayType type = key.type();
            if (type == OverlayType.WET && !rainEnabled) continue;
            if (type != OverlayType.WET && !seasonalEnabled) continue;
            if (!VisibilityManager.isChunkVisible(frustum, key.chunkPos(), minY, maxY)) continue;

            for (OverlayGpuMesh mesh : entry.getValue()) {
                if (!mesh.hasGeometry()) continue;

                mesh.vertexBuffer().bind();
                mesh.vertexBuffer().drawWithShader(modelView, projection, shader);

                renderedFaces += mesh.quadCount();
                activeTypeMask |= (1 << type.ordinal());
            }
        }

        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        finish(start, renderedFaces, Integer.bitCount(activeTypeMask));
    }

    private void finish(long start, int renderedFaces, int activeTypeCount) {
        hasRendered = true;
        lastRenderNanos = System.nanoTime() - start;
        lastRenderedFaces = renderedFaces;
        lastActiveLayerCount = activeTypeCount;
    }

    public void onChunkUnload(net.minecraft.world.level.ChunkPos chunkPos) {
        gpuCache.onChunkUnload(chunkPos);
    }

    @Override
    public void onConfigReload() {
        gpuCache.invalidateAll(invalidationQueue);
    }

    public void reset() {
        gpuCache.reset();
    }

    public OverlayGpuCache getGpuCache() { return gpuCache; }

    public boolean hasRendered() { return hasRendered; }
    public long lastRenderNanos() { return lastRenderNanos; }
    public int lastActiveLayerCount() { return lastActiveLayerCount; }
    public int lastRenderedFaces() { return lastRenderedFaces; }
    public int lastSkippedFaces() { return 0; }
    public int lastMergedFaces() { return 0; }
    public int cachedMeshCount() { return gpuCache.cachedMeshCount(); }
    public long invalidationsProcessed() { return gpuCache.invalidationsProcessed(); }
    public long staleDiscards() { return gpuCache.staleDiscards(); }
    public long cancelledRebuilds() { return gpuCache.cancelledRebuilds(); }
}