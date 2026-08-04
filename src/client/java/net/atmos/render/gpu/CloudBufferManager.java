package net.atmos.render.gpu;

import com.mojang.blaze3d.vertex.*;
import net.atmos.cloud.CloudLayer;

public final class CloudBufferManager {

    private CloudBufferManager() {}

    public static VertexBuffer upload(CloudLayer layer, float maxExtent, float brightness) {
        if (layer.texture() == null) return null;

        float half = Math.min(layer.scale(), maxExtent) * 0.5f;
        float shade = Math.min(1.0f, brightness);
        float alpha = layer.opacity();
        float y = layer.height();

        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()),
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(-half, y, -half).setUv(0f, 0f).setColor(shade, shade, shade, alpha);
        builder.addVertex(-half, y,  half).setUv(0f, 1f).setColor(shade, shade, shade, alpha);
        builder.addVertex( half, y,  half).setUv(1f, 1f).setColor(shade, shade, shade, alpha);
        builder.addVertex( half, y, -half).setUv(1f, 0f).setColor(shade, shade, shade, alpha);

        MeshData meshData = builder.buildOrThrow();
        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vertexBuffer.bind();
        vertexBuffer.upload(meshData);
        VertexBuffer.unbind();
        return vertexBuffer;
    }
}