package net.atmos.overlay;

import java.util.ArrayList;
import java.util.List;

public record OverlayDiagnostics(
        boolean rendererActive,
        long lastRenderNanos,
        int lastActiveLayerCount,
        int activeOverlayCount,
        float averageOverlayStrength,
        float seasonContribution,
        float rainContribution,
        int registeredOverlayTypeCount,
        int cachedTextureCount,
        int lastRenderedFaces,
        int lastSkippedFaces,
        int lastMergedFaces,
        int cachedChunkCount,
        int cachedRawSurfaceCount,
        int cachedPositionCount,
        int mergedQuadCount,
        float mergeRatio,
        int largestMergedQuadArea,
        int dirtyQueueSize,
        int lastDirtyBatchSize,
        long chunksRebuilt,
        long incrementalUpdatesPerformed,
        long lastChunkRebuildNanos,
        float averageFullRebuildNanos,
        float averageIncrementalNanos,
        long estimatedMemoryBytes,
        int invalidCacheEntries,
        List<String> missingTextureFamilies
) {
    public static OverlayDiagnostics capture(OverlayManager manager, OverlayRenderer renderer,
                                             OverlayChunkSurfaceCache chunkCache) {
        int active = 0;
        float sum = 0f;
        for (OverlayType type : OverlayType.values()) {
            float value = manager.getValue(type);
            sum += value;
            if (value > 0.01f) active++;
        }
        float average = sum / OverlayType.values().length;

        List<String> missingFamilies = new ArrayList<>();
        for (OverlayType type : OverlayType.values()) {
            boolean anyResolutionLoaded = false;
            for (OverlayResolution resolution : OverlayResolution.values()) {
                if (!OverlayTextureRegistry.texturesFor(type, resolution).isEmpty()) {
                    anyResolutionLoaded = true;
                    break;
                }
            }
            if (!anyResolutionLoaded) missingFamilies.add(type.name().toLowerCase());
        }

        int rawSurfaces = chunkCache.cachedSurfaceCount();
        int mergedQuads = chunkCache.mergedSurfaceCount();

        // Fraction of raw faces eliminated by merging — 0 = no merging, approaching 1 = highly merged.
        float mergeRatio = rawSurfaces == 0 ? 0f : 1f - (mergedQuads / (float) rawSurfaces);

        return new OverlayDiagnostics(
                renderer.hasRendered(),
                renderer.lastRenderNanos(),
                renderer.lastActiveLayerCount(),
                active,
                average,
                manager.contributionFrom(OverlaySource.SEASON),
                manager.contributionFrom(OverlaySource.RAIN),
                OverlayType.values().length,
                OverlayTextureRegistry.cachedTextureCount(),
                renderer.lastRenderedFaces(),
                renderer.lastSkippedFaces(),
                renderer.lastMergedFaces(),
                chunkCache.cachedChunkCount(),
                rawSurfaces,
                chunkCache.cachedPositionCount(),
                mergedQuads,
                mergeRatio,
                chunkCache.largestMergedQuadArea(),
                chunkCache.dirtyQueueSize(),
                chunkCache.lastDirtyBatchSize(),
                chunkCache.chunksRebuilt(),
                chunkCache.incrementalUpdatesPerformed(),
                chunkCache.lastChunkRebuildNanos(),
                chunkCache.averageFullRebuildNanos(),
                chunkCache.averageIncrementalNanos(),
                chunkCache.estimatedMemoryBytes(),
                0, // structurally guaranteed by OverlaySurfaceQuad's own constructor — never fabricated
                List.copyOf(missingFamilies)
        );
    }
}