package net.atmos.scheduling;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Generic, priority-ordered, budget-adaptive work scheduler — Batch 3
 * foundation. Deliberately not overlay-specific and lives outside
 * {@code net.atmos.overlay}: any Atmos system with incremental background
 * work (chunk scanning, mesh rebuilding, GPU uploads) may hold its own
 * instance, per Batch 3's "Must be generic. Not overlay specific. Future
 * systems should reuse it" requirement.
 *
 * Priority is a plain {@code int}; lower values are processed first,
 * matching the "Priority 1 = most urgent" convention already established
 * by {@link net.atmos.overlay.InvalidationPriority}. This class has no
 * knowledge of what priority levels mean to its caller.
 *
 * Adaptive budgeting: {@link #averageItemNanos()} is an exponentially
 * weighted running estimate of how long one processed item costs, updated
 * after every item. {@link #suggestItemBudget(long)} converts a target
 * nanosecond budget into an item count from that measured cost, so a
 * caller can size a per-tick budget from real behavior instead of a
 * hardcoded task count the way {@code GpuUploadScheduler} currently does.
 *
 * Batch 1 scope: standalone infrastructure. No existing scheduler
 * ({@code GpuUploadScheduler}, {@code AtmosTickScheduler},
 * {@code OverlaySimulationScheduler}) is modified or replaced by this
 * class in this batch.
 */
public final class AdaptivePriorityScheduler<T> {

    private static final double EMA_SMOOTHING = 0.2;

    private final Object lock = new Object();
    private final TreeMap<Integer, Deque<T>> tiers = new TreeMap<>();

    private long totalSubmitted = 0L;
    private long totalProcessed = 0L;
    private long lastDrainNanos = 0L;
    private int  lastDrainCount = 0;
    private double averageItemNanos = 0.0;

    public void submit(T item, int priority) {
        if (item == null) throw new IllegalArgumentException("item must not be null");
        synchronized (lock) {
            tiers.computeIfAbsent(priority, p -> new ArrayDeque<>()).add(item);
            totalSubmitted++;
        }
    }

    public int pendingCount() {
        synchronized (lock) {
            int total = 0;
            for (Deque<T> tier : tiers.values()) total += tier.size();
            return total;
        }
    }

    /**
     * Processes up to {@code maxItems} items, most urgent priority first,
     * or until {@code maxNanos} has elapsed (checked between items, so a
     * currently-running item is never interrupted mid-execution) — the
     * same "processed > 0" guard {@code GpuUploadScheduler.runBudgeted}
     * already uses, ensuring at least one item always runs even if the
     * budget is already exhausted.
     */
    public int drainBudgeted(int maxItems, long maxNanos, Consumer<T> consumer) {
        if (consumer == null) throw new IllegalArgumentException("consumer must not be null");

        long start = System.nanoTime();
        int processed = 0;

        while (processed < maxItems) {
            if (processed > 0 && (System.nanoTime() - start) > maxNanos) break;

            T item = pollHighestPriority();
            if (item == null) break;

            long itemStart = System.nanoTime();
            consumer.accept(item);
            recordItemCost(System.nanoTime() - itemStart);

            processed++;
        }

        synchronized (lock) {
            lastDrainNanos = System.nanoTime() - start;
            lastDrainCount = processed;
            totalProcessed += processed;
        }

        return processed;
    }

    /** Estimates how many items fit in {@code targetNanos} using the current measured average cost. */
    public int suggestItemBudget(long targetNanos) {
        synchronized (lock) {
            if (averageItemNanos <= 0.0) return Integer.MAX_VALUE; // no data yet — caller supplies its own ceiling
            return Math.max(1, (int) (targetNanos / averageItemNanos));
        }
    }

    private T pollHighestPriority() {
        synchronized (lock) {
            var entry = tiers.firstEntry();
            while (entry != null && entry.getValue().isEmpty()) {
                tiers.remove(entry.getKey());
                entry = tiers.firstEntry();
            }
            if (entry == null) return null;
            T item = entry.getValue().poll();
            if (entry.getValue().isEmpty()) tiers.remove(entry.getKey());
            return item;
        }
    }

    private void recordItemCost(long itemNanos) {
        synchronized (lock) {
            averageItemNanos = (averageItemNanos <= 0.0)
                    ? itemNanos
                    : averageItemNanos + (itemNanos - averageItemNanos) * EMA_SMOOTHING;
        }
    }

    public long totalSubmitted()     { synchronized (lock) { return totalSubmitted; } }
    public long totalProcessed()     { synchronized (lock) { return totalProcessed; } }
    public long lastDrainNanos()     { synchronized (lock) { return lastDrainNanos; } }
    public int  lastDrainCount()     { synchronized (lock) { return lastDrainCount; } }
    public double averageItemNanos() { synchronized (lock) { return averageItemNanos; } }

    public void reset() {
        synchronized (lock) {
            tiers.clear();
            totalSubmitted = 0L;
            totalProcessed = 0L;
            lastDrainNanos = 0L;
            lastDrainCount = 0;
            averageItemNanos = 0.0;
        }
    }
}