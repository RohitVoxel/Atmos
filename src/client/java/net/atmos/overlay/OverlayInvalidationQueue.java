package net.atmos.overlay;

import net.atmos.config.AtmosConfig;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/**
 * The single global invalidation seam — Batch 3 §6. Owned centrally
 * (AtmosClient), never by OverlayChunkSurfaceCache. Every simulation-side
 * producer (ChunkSurfaceIndex via OverlayChunkSurfaceCache, and
 * OverlaySurfaceStateStore's level-crossing scheduler) enqueues here;
 * only OverlayGpuCache drains it.
 */
public final class OverlayInvalidationQueue {

    private static final long ESTIMATED_BYTES_PER_ENTRY = 64L;

    private final Object lock = new Object();

    private final EnumMap<InvalidationPriority, LinkedHashSet<InvalidationKey>> tiers =
            new EnumMap<>(InvalidationPriority.class);
    private final Map<InvalidationKey, InvalidationPriority> currentTier = new HashMap<>();

    public record QueuedInvalidation(InvalidationKey key, InvalidationPriority priority) {}

    private long entriesEnqueued = 0L;
    private long entriesEvicted = 0L;
    private long entriesDrained = 0L;
    private long entriesDeduplicated = 0L;
    private long cancelledByChunkUnload = 0L;

    public OverlayInvalidationQueue() {
        for (InvalidationPriority priority : InvalidationPriority.values()) {
            tiers.put(priority, new LinkedHashSet<>());
        }
    }

    public void enqueue(InvalidationKey key, InvalidationPriority priority) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (priority == null) throw new IllegalArgumentException("priority must not be null");

        synchronized (lock) {
            InvalidationPriority existingTier = currentTier.get(key);
            if (existingTier != null) {
                entriesDeduplicated++;
                if (priority.ordinal() >= existingTier.ordinal()) return;
                tiers.get(existingTier).remove(key);
            }

            tiers.get(priority).add(key);
            currentTier.put(key, priority);
            entriesEnqueued++;

            evictIfOverCapacity();
        }
    }

    public Optional<QueuedInvalidation> pollHighestPriorityEntry() {
        synchronized (lock) {
            for (InvalidationPriority priority : InvalidationPriority.values()) {
                LinkedHashSet<InvalidationKey> tier = tiers.get(priority);
                var it = tier.iterator();
                if (it.hasNext()) {
                    InvalidationKey key = it.next();
                    it.remove();
                    currentTier.remove(key);
                    entriesDrained++;
                    return Optional.of(new QueuedInvalidation(key, priority));
                }
            }
            return Optional.empty();
        }
    }

    /** Removes every queued entry belonging to a chunk that has just unloaded — Batch 3 §8. */
    public void cancelChunk(ChunkPos chunkPos) {
        synchronized (lock) {
            var it = currentTier.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.getKey().chunkPos().equals(chunkPos)) {
                    tiers.get(entry.getValue()).remove(entry.getKey());
                    it.remove();
                    cancelledByChunkUnload++;
                }
            }
        }
    }

    public int size()                               { synchronized (lock) { return currentTier.size(); } }
    public int sizeAt(InvalidationPriority priority) { synchronized (lock) { return tiers.get(priority).size(); } }
    public long entriesEnqueued()                   { synchronized (lock) { return entriesEnqueued; } }
    public long entriesEvicted()                     { synchronized (lock) { return entriesEvicted; } }
    public long entriesDrained()                     { synchronized (lock) { return entriesDrained; } }
    public long entriesDeduplicated()                { synchronized (lock) { return entriesDeduplicated; } }
    public long cancelledByChunkUnload()             { synchronized (lock) { return cancelledByChunkUnload; } }

    public long estimatedMemoryBytes() {
        return (long) size() * ESTIMATED_BYTES_PER_ENTRY;
    }

    public void reset() {
        synchronized (lock) {
            for (LinkedHashSet<InvalidationKey> tier : tiers.values()) tier.clear();
            currentTier.clear();
            entriesEnqueued = 0L;
            entriesEvicted = 0L;
            entriesDrained = 0L;
            entriesDeduplicated = 0L;
            cancelledByChunkUnload = 0L;
        }
    }

    private void evictIfOverCapacity() {
        long maxBytes = AtmosConfig.get().overlay.safeOverlayQueueMemoryBytes();
        long maxEntries = Math.max(1L, maxBytes / ESTIMATED_BYTES_PER_ENTRY);

        while (currentTier.size() > maxEntries) {
            boolean evictedAny = false;
            InvalidationPriority[] priorities = InvalidationPriority.values();
            for (int i = priorities.length - 1; i >= 0; i--) {
                LinkedHashSet<InvalidationKey> tier = tiers.get(priorities[i]);
                var it = tier.iterator();
                if (it.hasNext()) {
                    InvalidationKey evicted = it.next();
                    it.remove();
                    currentTier.remove(evicted);
                    entriesEvicted++;
                    evictedAny = true;
                    break;
                }
            }
            if (!evictedAny) break;
        }
    }
}