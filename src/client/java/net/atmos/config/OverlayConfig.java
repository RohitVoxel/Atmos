package net.atmos.config;

public final class OverlayConfig {

    public boolean overlaysEnabled         = true;
    public boolean seasonalOverlaysEnabled = true;
    public boolean rainOverlaysEnabled     = true;
    public float   updateSpeed             = 1.0f;
    public int     renderDistanceBlocks    = 128;
    public boolean debugOverlays           = false;
    public boolean surfaceRendererEnabled = true;
    public float   surfaceDepthOffset     = 0.02f;

    public float safeSurfaceDepthOffset() {
        return Math.clamp(surfaceDepthOffset, 0.005f, 0.25f);
    }

    public float safeUpdateSpeed() {
        return Math.clamp(updateSpeed, 0.1f, 5.0f);
    }

    public int safeRenderDistanceBlocks() {
        return Math.clamp(renderDistanceBlocks, 16, 512);
    }

    public int    simulationRadiusChunks = 12;
    public String qualityTier            = "HIGH"; // ULTRA / HIGH / MEDIUM / LOW

    public int safeSimulationRadiusChunks() {
        return Math.clamp(simulationRadiusChunks, 2, 32);
    }

    public int quadBudgetForCurrentTier() {
        return switch (qualityTier.toUpperCase()) {
            case "ULTRA"  -> 6000;
            case "MEDIUM" -> 2500;
            case "LOW"    -> 1500;
            default       -> 4000; // HIGH
        };
    }

    // Batch 1 Phase 2 — dirty-update work budget per OVERLAY phase tick.
    public int dirtyUpdateBudget = 128;

    public int safeDirtyUpdateBudget() {
        return Math.clamp(dirtyUpdateBudget, 16, 4096);
    }

    // --- Batch 3 Foundation: Overlay Invalidation Pipeline ---
    // Governs only OverlayInvalidationQueue / OverlayDirtyEventFilter.
    // Unread by any other system until a later batch wires the pipeline
    // in — see those classes' docs. "Chunk Scan Budget" and "GPU Upload
    // Budget" are deferred to the batch that actually changes
    // ChunkSurfaceIndex/OverlayGpuCache behavior.
    public long overlayQueueMemoryBytes     = 5_242_880L; // 5 MB
    public int  dirtyEventFilterDelayTicks  = 4;
    public int  dirtyEventFilterMaxEntries  = 50_000;
    public long backgroundWorkerBudgetNanos = 2_000_000L; // 2ms
    public long overlayGpuRebuildBudgetNanos = 2_000_000L;

    public long safeOverlayGpuRebuildBudgetNanos() {
        return Math.clamp(overlayGpuRebuildBudgetNanos, 200_000L, 16_000_000L);
    }

    public int chunkScanBudget = 4;

    public int safeChunkScanBudget() {
        return Math.clamp(chunkScanBudget, 1, 64);
    }

    public long safeOverlayQueueMemoryBytes() {
        return Math.clamp(overlayQueueMemoryBytes, 262_144L, 268_435_456L);
    }

    public int safeDirtyEventFilterDelayTicks() {
        return Math.clamp(dirtyEventFilterDelayTicks, 1, 200);
    }

    public int safeDirtyEventFilterMaxEntries() {
        return Math.clamp(dirtyEventFilterMaxEntries, 100, 500_000);
    }

    public long safeBackgroundWorkerBudgetNanos() {
        return Math.clamp(backgroundWorkerBudgetNanos, 100_000L, 16_000_000L);
    }
}