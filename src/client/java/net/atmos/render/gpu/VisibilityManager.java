package net.atmos.render.gpu;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Shared frustum culling. No renderer may reimplement chunk-visibility logic locally. */
public final class VisibilityManager {

    private VisibilityManager() {}

    public static boolean isChunkVisible(Frustum frustum, ChunkPos chunkPos, int minY, int maxY) {
        if (frustum == null) return true;
        double minX = chunkPos.getMinBlockX();
        double minZ = chunkPos.getMinBlockZ();
        AABB box = new AABB(minX, minY, minZ, minX + 16.0, maxY, minZ + 16.0);
        return frustum.isVisible(box);
    }

    public static boolean isBoxVisible(Frustum frustum, AABB box) {
        return frustum == null || frustum.isVisible(box);
    }
}