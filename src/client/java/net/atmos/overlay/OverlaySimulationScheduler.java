package net.atmos.overlay;

import net.minecraft.world.level.ChunkPos;

/**
 * Batch 1 Phase 1 fix: intervals are now expressed in CLIENT TICKS, and the
 * caller must supply the current AtmosTickScheduler tick count instead of an
 * internal call counter. Previously intervalTicksFor() counted invocations
 * of simulate(), which was called once per RENDERED FRAME — meaning a
 * Tier 1 chunk simulated 600x/sec at 600 FPS instead of a fixed rate. This
 * class no longer has any notion of "how many times was I called" — it only
 * answers "is currentTick due, given lastRunTick and this chunk's tier."
 */
public final class OverlaySimulationScheduler {

    private OverlaySimulationScheduler() {}

    private static final int TIER1_CHUNKS = 6, TIER2_CHUNKS = 12, TIER3_CHUNKS = 20;
    private static final int TIER1_INTERVAL = 1, TIER2_INTERVAL = 5, TIER3_INTERVAL = 20;

    /** -1 = frozen (beyond simulation radius or beyond tier 3). Interval is in client ticks. */
    public static int intervalTicksFor(ChunkPos chunkPos, ChunkPos playerChunk, int simulationRadiusChunks) {
        int distance = Math.max(Math.abs(chunkPos.x - playerChunk.x), Math.abs(chunkPos.z - playerChunk.z));
        if (distance > simulationRadiusChunks) return -1;
        if (distance <= TIER1_CHUNKS) return TIER1_INTERVAL;
        if (distance <= TIER2_CHUNKS) return TIER2_INTERVAL;
        if (distance <= TIER3_CHUNKS) return TIER3_INTERVAL;
        return -1;
    }
}