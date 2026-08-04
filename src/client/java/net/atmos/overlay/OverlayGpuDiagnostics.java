package net.atmos.overlay;

import net.atmos.render.gpu.OverlayGpuCache;

public record OverlayGpuDiagnostics(
        int cachedMeshCount,
        int queuedForGpuCount,
        int currentlyBuildingCount,
        long invalidationsProcessed,
        long staleDiscards,
        long cancelledRebuilds,
        float averageRebuildNanos,
        float averageQueueLatencyTicks
) {
    public static OverlayGpuDiagnostics capture(OverlayGpuCache cache) {
        return new OverlayGpuDiagnostics(
                cache.cachedMeshCount(),
                cache.queuedForRebuildCount(),
                cache.currentlyBuildingCount(),
                cache.invalidationsProcessed(),
                cache.staleDiscards(),
                cache.cancelledRebuilds(),
                cache.averageRebuildNanos(),
                cache.averageQueueLatencyTicks()
        );
    }
}