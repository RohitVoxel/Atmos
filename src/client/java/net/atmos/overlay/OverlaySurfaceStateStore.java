package net.atmos.overlay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Owns every overlay surface's transition state (0.0 -> 10.0 scale).
 * Batch 3 §4: every published transition also registers with the shared
 * OverlayLevelCrossingScheduler, and crossings are drained into the
 * shared OverlayInvalidationQueue — never polled.
 */
public final class OverlaySurfaceStateStore {

    private static final float TARGET_CHANGE_EPSILON = 0.10f;

    private final Map<Long, EnumMap<OverlayType, OverlaySurfaceValue>> transitions = new HashMap<>();
    private final Map<Long, EnumMap<OverlayType, Integer>> faceMasks = new HashMap<>();

    private final OverlayLevelCrossingScheduler<SurfaceTransitionKey> scheduler;
    private final OverlayInvalidationQueue invalidationQueue;

    public OverlaySurfaceStateStore(OverlayLevelCrossingScheduler<SurfaceTransitionKey> scheduler,
                                    OverlayInvalidationQueue invalidationQueue) {
        this.scheduler = scheduler;
        this.invalidationQueue = invalidationQueue;
    }

    public float currentValue(BlockPos pos, OverlayType type, long currentTick) {
        EnumMap<OverlayType, OverlaySurfaceValue> perType = transitions.get(pos.asLong());
        if (perType == null) return 0f;
        OverlaySurfaceValue value = perType.get(type);
        return value == null ? 0f : value.interpolate(currentTick);
    }

    public float storedTarget(BlockPos pos, OverlayType type) {
        EnumMap<OverlayType, OverlaySurfaceValue> perType = transitions.get(pos.asLong());
        if (perType == null) return 0f;
        OverlaySurfaceValue value = perType.get(type);
        return value == null ? 0f : value.targetValue();
    }

    /** {@code faceMask}: bit i set (i = Direction.ordinal()) means this position has an exposed face in that direction. */
    public void setTarget(BlockPos pos, OverlayType type, float newTarget, long currentTick,
                          long durationTicks, int faceMask) {
        long key = pos.asLong();
        EnumMap<OverlayType, OverlaySurfaceValue> perType =
                transitions.computeIfAbsent(key, k -> new EnumMap<>(OverlayType.class));
        EnumMap<OverlayType, Integer> perTypeFaceMask =
                faceMasks.computeIfAbsent(key, k -> new EnumMap<>(OverlayType.class));

        OverlaySurfaceValue existing = perType.get(type);
        if (existing != null && Math.abs(existing.targetValue() - newTarget) < TARGET_CHANGE_EPSILON) {
            perTypeFaceMask.put(type, faceMask);
            return;
        }

        float startValue = existing == null ? 0f : existing.interpolate(currentTick);
        OverlaySurfaceValue value = new OverlaySurfaceValue(startValue, newTarget, currentTick, durationTicks);
        perType.put(type, value);
        perTypeFaceMask.put(type, faceMask);

        scheduler.registerTransition(new SurfaceTransitionKey(pos, type), value, OverlayLevelResolver::levelForScale10);
    }

    /** Block broken, placed, moved by piston, or otherwise replaced — starts at zero, never inherits. */
    public void clear(BlockPos pos) {
        long key = pos.asLong();
        transitions.remove(key);
        faceMasks.remove(key);
        scheduler.cancelIf(k -> k.pos().equals(pos));
    }

    /** Chunk unload — Batch 3 §8. Cancels every transition and scheduled crossing within the chunk. */
    public void cancelChunk(ChunkPos chunkPos) {
        scheduler.cancelIf(k -> new ChunkPos(k.pos()).equals(chunkPos));
        transitions.keySet().removeIf(packed -> new ChunkPos(BlockPos.of(packed)).equals(chunkPos));
        faceMasks.keySet().removeIf(packed -> new ChunkPos(BlockPos.of(packed)).equals(chunkPos));
    }

    /** Drains every level crossing due at {@code currentTick}, pushing resulting invalidations into the shared queue. */
    public void pumpLevelCrossings(long currentTick, Function<ChunkPos, InvalidationPriority> priorityResolver) {
        scheduler.pump(currentTick, transitionKey -> {
            BlockPos pos = transitionKey.pos();
            OverlayType type = transitionKey.type();
            ChunkPos chunkPos = new ChunkPos(pos);
            Integer faceMask = faceMaskFor(pos, type);
            if (faceMask == null) return;

            InvalidationPriority priority = priorityResolver.apply(chunkPos);
            for (Direction dir : Direction.values()) {
                if ((faceMask & (1 << dir.ordinal())) == 0) continue;
                invalidationQueue.enqueue(new InvalidationKey(chunkPos, type, dir), priority);
            }
        });
    }

    private Integer faceMaskFor(BlockPos pos, OverlayType type) {
        EnumMap<OverlayType, Integer> perType = faceMasks.get(pos.asLong());
        return perType == null ? null : perType.get(type);
    }

    public void reset() {
        transitions.clear();
        faceMasks.clear();
        scheduler.reset();
    }

    public int trackedPositionCount() {
        return transitions.size();
    }

    public int activeSurfaceCount() {
        int count = 0;
        for (EnumMap<OverlayType, OverlaySurfaceValue> perType : transitions.values()) {
            for (OverlaySurfaceValue v : perType.values()) {
                if (v.targetValue() > 0.1f) { count++; break; }
            }
        }
        return count;
    }
}