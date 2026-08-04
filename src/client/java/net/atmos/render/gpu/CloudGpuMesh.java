package net.atmos.render.gpu;

import com.mojang.blaze3d.vertex.VertexBuffer;

public final class CloudGpuMesh implements AutoCloseable {

    private final int layerIndex;
    private VertexBuffer vertexBuffer;
    private int bakedGeneration = -1;

    public CloudGpuMesh(int layerIndex) { this.layerIndex = layerIndex; }

    public int layerIndex() { return layerIndex; }
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