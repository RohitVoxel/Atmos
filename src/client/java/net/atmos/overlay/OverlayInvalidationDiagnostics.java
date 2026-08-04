package net.atmos.overlay;

public record OverlayInvalidationDiagnostics(
        long eventsReceived,
        long eventsMerged,
        long positionsEmitted,
        long positionsForcedFlushed,
        int trackedPositionCount,
        long batchesDrained,
        int queueSize,
        long queueEntriesEnqueued,
        long queueEntriesDeduplicated,
        long queueEstimatedMemoryBytes,
        long queueEntriesEvicted,
        long queueCancelledByChunkUnload,
        long scheduledCrossings,
        long firedCrossings,
        long cancelledCrossings,
        int pendingCrossings
) {
    public static OverlayInvalidationDiagnostics capture(
            OverlayTickBatchCollector batchCollector, OverlayInvalidationQueue queue,
            OverlayLevelCrossingScheduler<?> scheduler) {
        OverlayDirtyEventFilter filter = batchCollector.filter();

        return new OverlayInvalidationDiagnostics(
                filter.eventsReceived(),
                filter.eventsMerged(),
                filter.positionsEmitted(),
                filter.positionsForcedFlushed(),
                filter.trackedCount(),
                batchCollector.batchesDrained(),
                queue.size(),
                queue.entriesEnqueued(),
                queue.entriesDeduplicated(),
                queue.estimatedMemoryBytes(),
                queue.entriesEvicted(),
                queue.cancelledByChunkUnload(),
                scheduler.scheduledCount(),
                scheduler.firedCount(),
                scheduler.cancelledCount(),
                scheduler.pendingCount()
        );
    }
}