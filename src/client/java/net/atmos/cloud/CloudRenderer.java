package net.atmos.cloud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.atmos.config.AtmosConfig;
import net.atmos.render.gpu.CloudGpuCache;
import net.atmos.render.gpu.CloudGpuMesh;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Pure consumer of CloudManager's published layers via a retained CloudGpuCache.
 *
 * World-space fix (Batch 3 Phase 11): previously translated only Y, leaving
 * cloud geometry's absolute world X/Z uncompensated for camera position —
 * the root cause of clouds appearing fixed relative to the camera. Now
 * matches OverlayRenderer's already-correct full X/Y/Z translate. No
 * change to mesh geometry, cloud simulation, or layer architecture.
 */
public final class CloudRenderer {

    private final CloudManager cloudManager;
    private final CloudGpuCache gpuCache = new CloudGpuCache();

    private boolean hasRendered = false;
    private long lastRenderNanos = 0L;
    private int lastActiveLayerCount = 0;

    public CloudRenderer(CloudManager cloudManager) {
        this.cloudManager = cloudManager;
    }

    public void render(WorldRenderContext context) {
        long start = System.nanoTime();
        int drawn = 0;

        if (AtmosConfig.get().cloud.cloudsEnabled && cloudManager.isInitialized()) {
            CloudRenderState state = cloudManager.getRenderState();
            Vec3 cameraPos = context.camera().getPosition();
            float brightness = AtmosConfig.get().cloud.safeBrightness();
            float maxExtent = AtmosConfig.get().cloud.safeRenderDistanceBlocks();

            for (CloudLayer layer : state.layers()) {
                if (!layer.enabled()) continue;
                gpuCache.requestRebuildIfNeeded(layer, maxExtent, brightness);
            }
            gpuCache.scheduler().runBudgeted(8, 1_000_000L);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            var shader = RenderSystem.getShader();

            // Camera-relative transform — identical for every layer this frame.
            Matrix4f modelView = new Matrix4f(context.matrixStack().last().pose())
                    .translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);

            for (CloudLayer layer : state.layers()) {
                if (!layer.enabled()) continue;
                CloudGpuMesh mesh = gpuCache.meshFor(layer.index());
                if (mesh == null || !mesh.hasGeometry()) continue;

                RenderSystem.setShaderTexture(0, layer.texture());

                mesh.vertexBuffer().bind();
                mesh.vertexBuffer().drawWithShader(modelView, context.projectionMatrix(), shader);
                drawn++;
            }

            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        hasRendered = true;
        lastRenderNanos = System.nanoTime() - start;
        lastActiveLayerCount = drawn;
    }

    public void reset() {
        gpuCache.reset();
    }

    public boolean hasRendered() { return hasRendered; }
    public long lastRenderNanos() { return lastRenderNanos; }
    public int lastActiveLayerCount() { return lastActiveLayerCount; }
}