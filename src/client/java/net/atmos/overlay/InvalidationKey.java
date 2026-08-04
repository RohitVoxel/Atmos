package net.atmos.overlay;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * Identity of one overlay mesh bucket needing rebuild — the single unit
 * that will cross the simulation -> rendering seam once the Batch 3
 * migration plan wires {@link OverlayInvalidationQueue} into
 * {@code OverlayGpuCache}. Field composition intentionally matches
 * {@code OverlayGpuCache}'s existing private {@code Key} record exactly,
 * since both identify the same mesh bucket.
 */
public record InvalidationKey(ChunkPos chunkPos, OverlayType type, Direction face) {
    public InvalidationKey {
        if (chunkPos == null) throw new IllegalArgumentException("chunkPos must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (face == null) throw new IllegalArgumentException("face must not be null");
    }
}