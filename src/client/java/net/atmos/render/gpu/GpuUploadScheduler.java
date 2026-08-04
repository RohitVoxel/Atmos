package net.atmos.render.gpu;

import java.util.ArrayDeque;
import java.util.Queue;

public final class GpuUploadScheduler {

    private final Queue<Runnable> pending = new ArrayDeque<>();
    private int lastBatchSize = 0;
    private long lastRunNanos = 0L;

    public void enqueue(Runnable rebuildTask) {
        pending.add(rebuildTask);
    }

    public void runBudgeted(int maxTasks, long maxNanos) {
        long start = System.nanoTime();
        int processed = 0;
        while (!pending.isEmpty() && processed < maxTasks) {
            if (processed > 0 && (System.nanoTime() - start) > maxNanos) break;
            pending.poll().run();
            processed++;
        }
        lastBatchSize = processed;
        lastRunNanos = System.nanoTime() - start;
    }

    public int pendingCount() { return pending.size(); }
    public int lastBatchSize() { return lastBatchSize; }
    public long lastRunNanos() { return lastRunNanos; }

    public void clear() { pending.clear(); }
}