package net.atmos.overlay;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * Simulation-side surface geometry access for the mesh-building stage.
 *
 * No method here enumerates "everything currently loaded" — that bulk
 * iteration existed solely to support per-frame polling and is removed
 * as of Batch 3 (see OverlayChunkSurfaceCache's now-deleted
 * cachedChunksByFace mechanism). Consumers instead react to explicit
 * invalidation events and look up exactly the one (chunk, face) bucket
 * they were told changed.
 */
public interface OverlaySurfaceProvider {

    List<OverlaySurfaceQuad> quadsFor(ChunkPos chunkPos, Direction face, ClientLevel level);

    /** Current geometry generation of the given chunk, or -1 if the chunk is not currently loaded. */
    long chunkGeneration(ChunkPos chunkPos);
}