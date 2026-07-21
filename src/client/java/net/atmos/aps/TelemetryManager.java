package net.atmos.aps;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free publisher for {@link TelemetrySnapshot} — Appendix D §2,
 * mirroring the Camera Snapshot Contract (Appendix F §1). Render Thread is
 * the sole writer; any thread may read. No collection call site exists yet
 * — {@link #publish} is the architectural contract only, awaiting a future
 * Render Thread hook. Holds only the latest frame; history belongs to APS.
 */
public final class TelemetryManager {

    private TelemetryManager() {}

    private static final AtomicReference<TelemetrySnapshot> CURRENT = new AtomicReference<>(null);
    private static final AtomicLong FRAME_NUMBER = new AtomicLong(0L);

    /** Render Thread only. */
    public static void publish(float frameTimeMs, float atmosRenderCpuMs,
                               int visibleClusterCount, int activeRayCount, int loadedCells) {
        long frameNumber = FRAME_NUMBER.incrementAndGet();
        CURRENT.set(new TelemetrySnapshot(
                frameTimeMs, atmosRenderCpuMs, visibleClusterCount, activeRayCount,
                loadedCells, System.nanoTime(), frameNumber));
    }

    /** Latest published snapshot, or {@code null} before the first publish. */
    public static TelemetrySnapshot get() {
        return CURRENT.get();
    }

    public static void reset() {
        CURRENT.set(null);
        FRAME_NUMBER.set(0L);
    }
}