package net.atmos.render.gpu;

import net.atmos.overlay.*;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.*;

/** Pure classification: groups already-merged quads by current accumulation level. No GPU work. */
public final class OverlayMeshBuilder {

    private OverlayMeshBuilder() {}

    public record ChunkFaceMeshes(Map<Integer, List<OverlaySurfaceQuad>> quadsByLevel) {}

    public static ChunkFaceMeshes classify(List<OverlaySurfaceQuad> quads, OverlaySurfaceStateStore stateStore,
                                           OverlayType type, long currentTick, ClientLevel level) {
        Map<Integer, List<OverlaySurfaceQuad>> byLevel = new HashMap<>();
        for (OverlaySurfaceQuad quad : quads) {
            if (!level.getBlockState(quad.origin()).equals(quad.representativeState())) continue;
            float accumulation = stateStore.currentValue(quad.origin(), type, currentTick);
            int lvl = OverlayLevelResolver.levelForScale10(accumulation);
            if (lvl <= 0) continue;
            byLevel.computeIfAbsent(lvl, k -> new ArrayList<>()).add(quad);
        }
        return new ChunkFaceMeshes(byLevel);
    }
}