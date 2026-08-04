package net.atmos.overlay;

import java.util.Map;

public record OverlayChunkLifecycleDiagnostics(
        int scanQueueSize,
        Map<ChunkLifecycleState, Integer> counts
) {
    public static OverlayChunkLifecycleDiagnostics capture(OverlayChunkSurfaceCache cache) {
        return new OverlayChunkLifecycleDiagnostics(cache.scanQueueSize(), cache.lifecycleCounts());
    }
}