package net.atmos.render.gpu;

import com.mojang.blaze3d.vertex.VertexBuffer;

/** Reserved for a future fog geometry producer (e.g. ground-fog quads). Unused by FogRenderer today. */
public final class FogGpuMesh implements AutoCloseable {

    private VertexBuffer vertexBuffer;
    private int bakedGeneration = -1;

    public VertexBuffer vertexBuffer() { return vertexBuffer; }
    public boolean hasGeometry() { return vertexBuffer != null; }
    public int bakedGeneration() { return bakedGeneration; }

    void assign(VertexBuffer newBuffer, int generation) {
        if (vertexBuffer != null && vertexBuffer != newBuffer) vertexBuffer.close();
        vertexBuffer = newBuffer;
        bakedGeneration = generation;
    }

    @Override
    public void close() {
        if (vertexBuffer != null) { vertexBuffer.close(); vertexBuffer = null; }
    }
}