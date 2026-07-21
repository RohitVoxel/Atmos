package net.atmos.aps;

/**
 * Immutable, single-frame raw telemetry — Appendix D §2 (canonical
 * TelemetrySnapshot contract: frameTimeMs, atmosRenderCpuMs,
 * visibleClusterCount, activeRayCount, loadedCells, timestamp, frameNumber).
 * Published exclusively by the Render Thread via {@link TelemetryManager};
 * consumed by APS on the Simulation Thread. Represents exactly one
 * completed frame — APS owns aggregation/history, never this record.
 */
public record TelemetrySnapshot(
        float frameTimeMs,
        float atmosRenderCpuMs,
        int visibleClusterCount,
        int activeRayCount,
        int loadedCells,
        long timestamp,
        long frameNumber
) {
    public TelemetrySnapshot {
        requireFinite("frameTimeMs", frameTimeMs);
        requireFinite("atmosRenderCpuMs", atmosRenderCpuMs);
        if (visibleClusterCount < 0) throw new IllegalArgumentException("visibleClusterCount must be non-negative, got " + visibleClusterCount);
        if (activeRayCount < 0) throw new IllegalArgumentException("activeRayCount must be non-negative, got " + activeRayCount);
        if (loadedCells < 0) throw new IllegalArgumentException("loadedCells must be non-negative, got " + loadedCells);
        if (frameNumber < 0) throw new IllegalArgumentException("frameNumber must be non-negative, got " + frameNumber);
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite, got " + value);
    }
}