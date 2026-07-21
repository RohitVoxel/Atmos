package net.atmos.aps;

/**
 * Render Thread telemetry collector — Chapter 16 Stage 2 (Appendix D §2).
 * Wraps the Atmos per-frame update block with timing only — no rendering,
 * no simulation, no optimization logic.
 *
 * Stage 2 approximations (temporary, until later stages supply real data):
 *
 *   frameTimeMs — measured as the interval between consecutive
 *   beginFrame() calls (i.e. the previous frame's duration), since no
 *   dedicated end-of-frame render hook exists yet. Not a true
 *   start-of-frame-to-present measurement.
 *
 *   atmosUpdateCpuMs (published into TelemetrySnapshot.atmosRenderCpuMs)
 *   — measures the entire Atmos per-frame update block (FogManager,
 *   CellGrid, CellMemoryIntegrator, sky context capture, etc.), NOT
 *   renderer time. ALSSRenderer is not wired into the live per-frame loop
 *   yet, so no actual render-pass cost is measured. Field name in
 *   TelemetrySnapshot is a frozen Stage 1 contract and is not renamed
 *   here; this doc is the authoritative clarification of what it
 *   currently measures.
 *
 *   visibleClusterCount / activeRayCount — always published as 0.
 *   These are NOT measurements of zero activity; they are UNAVAILABLE,
 *   because the Composition Engine -> RenderCluster -> ALSSRenderer
 *   pipeline is not wired into the live per-frame loop. Do not interpret
 *   0 as "no clusters/rays this frame."
 *
 * timestamp (TelemetrySnapshot.timestamp, sourced from System.nanoTime()
 * in TelemetryManager) exists solely for snapshot ordering and developer
 * diagnostics. It must never be read by ALSC or any future optimization
 * decision — Appendix D §2 defines TelemetrySnapshot as raw per-frame
 * telemetry, not a scheduling or gameplay-timing primitive.
 *
 * Canonical collection point: WorldRenderEvents.START, matching every
 * other per-frame Atmos controller in AtmosClient. If ALSS rendering is
 * later moved to a different render callback, this collector's
 * beginFrame()/endFrame() calls must move with it so atmosUpdateCpuMs
 * continues to bracket the correct span of work.
 */
public final class TelemetryCollector {

    private long frameStartNanos = -1L;
    private long previousFrameStartNanos = -1L;

    /** Call once, first, before any Atmos per-frame update work. */
    public void beginFrame() {
        previousFrameStartNanos = frameStartNanos;
        frameStartNanos = System.nanoTime();
    }

    /** Call once, last, after all Atmos per-frame update work completes. */
    public void endFrame(int loadedCells) {
        long now = System.nanoTime();
        float atmosUpdateCpuMs = (frameStartNanos < 0) ? 0f
                : (now - frameStartNanos) / 1_000_000f;
        float frameTimeMs = (previousFrameStartNanos < 0) ? 0f
                : (frameStartNanos - previousFrameStartNanos) / 1_000_000f;

        // visibleClusterCount / activeRayCount: unavailable, not zero-valued
        // measurements — see class doc.
        TelemetryManager.publish(frameTimeMs, atmosUpdateCpuMs, 0, 0, loadedCells);
    }

    /** Discards timing continuity — call on dimension change and disconnect. */
    public void reset() {
        frameStartNanos = -1L;
        previousFrameStartNanos = -1L;
    }
}