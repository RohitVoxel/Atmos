package net.atmos.overlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Generic exact-tick level-crossing scheduler — Batch 3 §5.
 *
 * Given a continuous smoothstep value curve ({@link OverlaySurfaceValue})
 * and a caller-supplied bucket function, predicts the exact tick(s) at
 * which the bucket changes and fires a callback then — never before,
 * never by periodic recheck. Generic over a key type K so future Atmos
 * systems (cloud density tiers, haze bands, seasonal thresholds) can
 * reuse this without depending on overlays at all.
 *
 * Crossing ticks are found by integer bisection directly on the tick
 * domain rather than by inverting the bucket function analytically —
 * this keeps the bucket function a true black box (any ToIntFunction
 * works) at the cost of O(log durationTicks) evaluations per crossing,
 * which only ever runs once per registered transition, never per frame.
 */
public final class OverlayLevelCrossingScheduler<K> {

    private static final int MAX_BISECTION_ITERATIONS = 48;

    private record ScheduledCrossing<K>(long fireTick, long sequence, K key) {}

    private final PriorityQueue<ScheduledCrossing<K>> heap = new PriorityQueue<>(
            Comparator.<ScheduledCrossing<K>>comparingLong(ScheduledCrossing::fireTick)
                    .thenComparingLong(ScheduledCrossing::sequence));
    private final Map<K, List<ScheduledCrossing<K>>> byKey = new HashMap<>();

    private long sequenceCounter = 0L;
    private long scheduledCount = 0L;
    private long firedCount = 0L;
    private long cancelledCount = 0L;

    /** Replaces any existing registration for {@code key}, then schedules every bucket crossing between start and target. */
    public void registerTransition(K key, OverlaySurfaceValue value, ToIntFunction<Float> bucketFunction) {
        cancel(key);

        long startTick = value.startTick();
        long endTick = startTick + value.durationTicks();

        int startBucket = bucketFunction.applyAsInt(value.interpolate(startTick));
        int endBucket = bucketFunction.applyAsInt(value.interpolate(endTick));
        if (startBucket == endBucket) return;

        int step = endBucket > startBucket ? 1 : -1;
        List<ScheduledCrossing<K>> crossings = new ArrayList<>();
        long searchLow = startTick;

        for (int bucket = startBucket; bucket != endBucket; bucket += step) {
            int nextBucket = bucket + step;
            long crossingTick = bisectCrossingTick(value, bucketFunction, searchLow, endTick, nextBucket, step);
            if (crossingTick < 0) break;

            ScheduledCrossing<K> crossing = new ScheduledCrossing<>(crossingTick, sequenceCounter++, key);
            crossings.add(crossing);
            heap.add(crossing);
            scheduledCount++;
            searchLow = crossingTick;
        }

        if (!crossings.isEmpty()) byKey.put(key, crossings);
    }

    public void cancel(K key) {
        List<ScheduledCrossing<K>> existing = byKey.remove(key);
        if (existing != null) {
            heap.removeAll(existing);
            cancelledCount += existing.size();
        }
    }

    public void cancelIf(Predicate<K> predicate) {
        Iterator<Map.Entry<K, List<ScheduledCrossing<K>>>> it = byKey.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (predicate.test(entry.getKey())) {
                heap.removeAll(entry.getValue());
                cancelledCount += entry.getValue().size();
                it.remove();
            }
        }
    }

    /** Fires every crossing due at or before {@code currentTick}. Call once per tick. */
    public void pump(long currentTick, Consumer<K> onCrossing) {
        while (!heap.isEmpty() && heap.peek().fireTick() <= currentTick) {
            ScheduledCrossing<K> crossing = heap.poll();
            List<ScheduledCrossing<K>> siblings = byKey.get(crossing.key());
            if (siblings != null) {
                siblings.remove(crossing);
                if (siblings.isEmpty()) byKey.remove(crossing.key());
            }
            firedCount++;
            onCrossing.accept(crossing.key());
        }
    }

    private long bisectCrossingTick(OverlaySurfaceValue value, ToIntFunction<Float> bucketFunction,
                                    long lowTick, long highTick, int targetBucket, int step) {
        long lo = lowTick;
        long hi = highTick;
        if (!reached(bucketFunction.applyAsInt(value.interpolate(hi)), targetBucket, step)) {
            return -1;
        }
        for (int i = 0; i < MAX_BISECTION_ITERATIONS && hi > lo; i++) {
            long mid = lo + (hi - lo) / 2;
            int midBucket = bucketFunction.applyAsInt(value.interpolate(mid));
            if (reached(midBucket, targetBucket, step)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return hi;
    }

    private static boolean reached(int bucket, int targetBucket, int step) {
        return step > 0 ? bucket >= targetBucket : bucket <= targetBucket;
    }

    public int pendingCount()     { return heap.size(); }
    public long scheduledCount()  { return scheduledCount; }
    public long firedCount()      { return firedCount; }
    public long cancelledCount()  { return cancelledCount; }

    public void reset() {
        heap.clear();
        byKey.clear();
        sequenceCounter = 0L;
        scheduledCount = 0L;
        firedCount = 0L;
        cancelledCount = 0L;
    }
}