package net.atmos.overlay;

import java.util.List;

/**
 * Batch 3 — collects {@link OverlayDirtyEventFilter} output into one
 * deduplicated batch per overlay tick, so downstream processing (a future
 * {@code ChunkSurfaceIndex.applyBatch}) drains at most once per tick
 * rather than reacting to every individually-filtered event.
 *
 * Batch 1 scope: standalone infrastructure — {@link #drainTick()} is not
 * yet called from {@code OverlayChunkSurfaceCache}'s tick path.
 */
public final class OverlayTickBatchCollector {

    private final OverlayDirtyEventFilter filter;
    private long currentTick = 0L;

    private long batchesDrained = 0L;
    private long positionsBatched = 0L;

    public OverlayTickBatchCollector(OverlayDirtyEventFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        this.filter = filter;
    }

    /** Records one raw block-change event at the current tick. */
    public void record(long packedPos) {
        filter.record(packedPos, currentTick);
    }

    /**
     * Drains every position stabilized as of the current tick, then
     * advances to the next tick. Call at most once per overlay tick.
     */
    public List<Long> drainTick() {
        List<Long> batch = filter.drainStabilized(currentTick);
        currentTick++;
        batchesDrained++;
        positionsBatched += batch.size();
        return batch;
    }

    public long currentTick()      { return currentTick; }
    public long batchesDrained()   { return batchesDrained; }
    public long positionsBatched() { return positionsBatched; }
    public OverlayDirtyEventFilter filter() { return filter; }

    public void reset() {
        filter.reset();
        currentTick = 0L;
        batchesDrained = 0L;
        positionsBatched = 0L;
    }
}