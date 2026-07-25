package net.atmos.diagnostics;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import java.util.Arrays;

public final class DiagnosticManager {
    public static volatile DiagnosticMode MODE = DiagnosticMode.OFF;

    private static final int STAGE_COUNT = PipelineStage.values().length;
    private static final RingBuffer<FrameSnapshot> HISTORY = new RingBuffer<>(DiagnosticConfig.DEFAULT.historyCapacity());

    private static long frameSequence = 0;
    private static final long[] stageStarts = new long[STAGE_COUNT];
    private static final long[] stageTimings = new long[STAGE_COUNT];
    private static final int[] events = new int[DiagnosticEvent.values().length];
    private static AnomalyType lastAnomaly = null;
    private static FullDiagnosticContext currentFullContext;

    private DiagnosticManager() {}

    public static boolean isActive() { return MODE != DiagnosticMode.OFF; }

    public static void beginFrame() {
        if (!isActive()) return;
        frameSequence++;
        lastAnomaly = null;
        Arrays.fill(stageStarts, 0L);
        Arrays.fill(stageTimings, 0L);
        Arrays.fill(events, 0);
        if (MODE == DiagnosticMode.FULL) currentFullContext = new FullDiagnosticContext();
    }

    public static void recordStageStart(PipelineStage stage) {
        if (!isActive()) return;
        stageStarts[stage.ordinal()] = DiagnosticClock.nanoTime();
    }

    public static void recordStageEnd(PipelineStage stage) {
        if (!isActive()) return;
        int idx = stage.ordinal();
        long start = stageStarts[idx];
        if (start == 0L) return;
        stageTimings[idx] = DiagnosticClock.nanoTime() - start;
        stageStarts[idx] = 0L;
    }

    public static void incrementEvent(DiagnosticEvent event, int count) {
        if (!isActive()) return;
        events[event.ordinal()] += count;
    }

    // Restored anomaly recorder
    public static void recordAnomaly(PipelineStage stage, AnomalyType type) {
        if (!isActive()) return;
        lastAnomaly = type;
        incrementEvent(DiagnosticEvent.ANOMALY_DETECTED, 1);
    }

    public static void endFrame(double cx, double cy, double cz, float rain, float thunder, Holder<Biome> biome, String dim) {
        if (!isActive()) return;
        if (MODE == DiagnosticMode.FULL) PersistentShaftTracker.trackFrame(frameSequence, currentFullContext);
        FrameSnapshot snapshot = new FrameSnapshot(frameSequence, DiagnosticClock.nanoTime(), cx, cy, cz, rain, thunder, biome, dim, stageTimings, new int[0], new int[0], new int[0], new int[0], events, lastAnomaly);
        HISTORY.add(snapshot);
    }

    public static FullDiagnosticContext getFullContext() { return currentFullContext; }
    public static RingBuffer<FrameSnapshot> getHistory() { return HISTORY; }
}