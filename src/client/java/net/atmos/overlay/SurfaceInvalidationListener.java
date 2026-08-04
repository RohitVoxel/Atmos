package net.atmos.overlay;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * Reusable push-based notification of a geometry-level surface change —
 * Batch 3 §2, replacing the previous bare Consumer<Direction> callback
 * ChunkSurfaceIndex used internally.
 *
 * overlayType is deliberately absent: ChunkSurfaceIndex has no concept of
 * overlay types — a geometry change on one face affects every overlay
 * type that renders on that face equally. The type fanout happens one
 * layer up (OverlayChunkSurfaceCache), where the receiver already knows
 * which overlay types exist.
 */
@FunctionalInterface
public interface SurfaceInvalidationListener {
    void onSurfaceInvalidated(ChunkPos chunkPos, Direction face, long generation, InvalidationReason reason);
}