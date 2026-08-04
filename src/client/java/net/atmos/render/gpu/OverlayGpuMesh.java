package net.atmos.render.gpu;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/** One retained, GPU-resident overlay mesh for a (chunk, face, accumulation-level) bucket. */
public final class OverlayGpuMesh implements AutoCloseable {

    private final ChunkPos chunkPos;
    private final Direction face;
    private final int level;

    private VertexBuffer vertexBuffer;
    private int quadCount;
    private long bakedGeneration = -1L;
    private long lastRebuildTick = -1L;

    public OverlayGpuMesh(ChunkPos chunkPos, Direction face, int level) {
        this.chunkPos = chunkPos;
        this.face = face;
        this.level = level;
    }

    public ChunkPos chunkPos() { return chunkPos; }
    public Direction face() { return face; }
    public int level() { return level; }
    public VertexBuffer vertexBuffer() { return vertexBuffer; }
    public boolean hasGeometry() { return vertexBuffer != null && quadCount > 0; }
    public int quadCount() { return quadCount; }
    public long bakedGeneration() { return bakedGeneration; }
    public long lastRebuildTick() { return lastRebuildTick; }

    void assign(VertexBuffer newBuffer, int quadCount, long bakedGeneration, long tick) {
        if (this.vertexBuffer != null && this.vertexBuffer != newBuffer) this.vertexBuffer.close();
        this.vertexBuffer = newBuffer;
        this.quadCount = quadCount;
        this.bakedGeneration = bakedGeneration;
        this.lastRebuildTick = tick;
    }

    void clearEmpty(long bakedGeneration, long tick) {
        if (this.vertexBuffer != null) { this.vertexBuffer.close(); this.vertexBuffer = null; }
        this.quadCount = 0;
        this.bakedGeneration = bakedGeneration;
        this.lastRebuildTick = tick;
    }

    @Override
    public void close() {
        if (vertexBuffer != null) { vertexBuffer.close(); vertexBuffer = null; }
    }
}