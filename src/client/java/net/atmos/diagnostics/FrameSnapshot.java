package net.atmos.diagnostics;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Immutable snapshot of a single frame's diagnostic state.
 * Arrays are defensively cloned. Empty constants prevent zero-length allocations.
 */
public record FrameSnapshot(
        long frameNumber,
        long timestampNs,
        double camX, double camY, double camZ,
        float rainLevel, float thunderLevel,
        Holder<Biome> biome,
        String dimension,
        long[] stageTimingsNs,
        int[] rejectionCounts,
        int[] rejectionReasons,
        int[] warningCounts,
        int[] anomalyCounts,
        int[] pipelineEvents,
        AnomalyType lastAnomaly
) {
    private static final long[] EMPTY_LONGS = new long[0];
    private static final int[] EMPTY_INTS = new int[0];

    public FrameSnapshot {
        stageTimingsNs = stageTimingsNs != null ? stageTimingsNs.clone() : EMPTY_LONGS;
        rejectionCounts = rejectionCounts != null ? rejectionCounts.clone() : EMPTY_INTS;
        rejectionReasons = rejectionReasons != null ? rejectionReasons.clone() : EMPTY_INTS;
        warningCounts = warningCounts != null ? warningCounts.clone() : EMPTY_INTS;
        anomalyCounts = anomalyCounts != null ? anomalyCounts.clone() : EMPTY_INTS;
        pipelineEvents = pipelineEvents != null ? pipelineEvents.clone() : EMPTY_INTS;
    }
}