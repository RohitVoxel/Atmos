package net.atmos.overlay;

import net.atmos.config.AtmosConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch 3 — generic, block-agnostic high-frequency change stabilization.
 * Tracks per-position event timing only; contains no block-identity
 * awareness whatsoever, so it protects against any high-frequency
 * contraption (cobblestone generators, piston doors, redstone clocks,
 * flying machines, farms not yet invented) without a whitelist or
 * block-ID check of any kind.
 *
 * A position is emitted exactly once, either when it stops changing for
 * {@code AtmosConfig.overlay.dirtyEventFilterDelayTicks} ticks (natural
 * stabilization), or once it has been continuously active for
 * {@link #MAX_DEFERRAL_MULTIPLIER} times that delay (forced flush) —
 * guaranteeing a permanently-active contraption is still eventually
 * processed rather than starving forever.
 *
 * Bounded by {@code AtmosConfig.overlay.dirtyEventFilterMaxEntries} via an
 * access-order {@link LinkedHashMap}, mirroring the exact
 * Map + {@code removeEldestEntry} eviction idiom already used by
 * {@code CellGrid}'s cached tier.
 *
 * Batch 1 scope: standalone infrastructure. Not yet wired into
 * {@code OverlayChunkSurfaceCache.markDirty()} — the existing unfiltered
 * {@code pendingBlockUpdates} queue continues to operate unchanged until a
 * later batch performs that wiring.
 */
public final class OverlayDirtyEventFilter {

    // Forced-flush ceiling, expressed as a multiple of the configured
    // stabilization delay. Not independently configurable — see class doc.
    private static final int MAX_DEFERRAL_MULTIPLIER = 20;

    private record TrackedEntry(long firstEventTick, long lastEventTick) {}

    private final Object lock = new Object();

    // Positions evicted by the LRU cap before their stabilization window
    // elapsed. Drained alongside naturally-stabilized positions.
    private final Deque<Long> forcedFlush = new ArrayDeque<>();

    private long eventsReceived = 0L;
    private long eventsMerged = 0L;
    private long positionsEmitted = 0L;
    private long positionsForcedFlushed = 0L;

    private final LinkedHashMap<Long, TrackedEntry> tracked =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, TrackedEntry> eldest) {
                    int max = AtmosConfig.get().overlay.safeDirtyEventFilterMaxEntries();
                    boolean evict = size() > max;
                    if (evict) {
                        forcedFlush.add(eldest.getKey());
                        positionsForcedFlushed++;
                    }
                    return evict;
                }
            };

    /** Records one raw change event for {@code packedPos} (e.g. {@code BlockPos.asLong()}) at {@code currentTick}. */
    public void record(long packedPos, long currentTick) {
        synchronized (lock) {
            eventsReceived++;
            TrackedEntry existing = tracked.get(packedPos);
            if (existing == null) {
                tracked.put(packedPos, new TrackedEntry(currentTick, currentTick));
            } else {
                eventsMerged++;
                tracked.put(packedPos, new TrackedEntry(existing.firstEventTick(), currentTick));
            }
        }
    }

    /**
     * Removes and returns every position ready to emit at {@code currentTick} —
     * those stabilized (no event for the configured delay) or forcibly
     * flushed (LRU-evicted, or continuously active past the deferral ceiling).
     */
    public List<Long> drainStabilized(long currentTick) {
        synchronized (lock) {
            List<Long> ready = new ArrayList<>();

            while (!forcedFlush.isEmpty()) {
                ready.add(forcedFlush.poll());
                positionsEmitted++;
            }

            int delay = AtmosConfig.get().overlay.safeDirtyEventFilterDelayTicks();
            long maxDeferral = (long) delay * MAX_DEFERRAL_MULTIPLIER;

            var it = tracked.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, TrackedEntry> entry = it.next();
                TrackedEntry state = entry.getValue();
                boolean stable = (currentTick - state.lastEventTick()) >= delay;
                boolean deferralForced = (currentTick - state.firstEventTick()) >= maxDeferral;
                if (stable || deferralForced) {
                    ready.add(entry.getKey());
                    it.remove();
                    positionsEmitted++;
                    if (deferralForced && !stable) positionsForcedFlushed++;
                }
            }

            return ready;
        }
    }

    public int trackedCount()            { synchronized (lock) { return tracked.size(); } }
    public long eventsReceived()         { synchronized (lock) { return eventsReceived; } }
    public long eventsMerged()           { synchronized (lock) { return eventsMerged; } }
    public long positionsEmitted()       { synchronized (lock) { return positionsEmitted; } }
    public long positionsForcedFlushed() { synchronized (lock) { return positionsForcedFlushed; } }

    public void reset() {
        synchronized (lock) {
            tracked.clear();
            forcedFlush.clear();
            eventsReceived = 0L;
            eventsMerged = 0L;
            positionsEmitted = 0L;
            positionsForcedFlushed = 0L;
        }
    }
}