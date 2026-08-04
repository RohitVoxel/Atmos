package net.atmos.render.gpu;

import net.atmos.overlay.*;
import net.atmos.scheduling.AdaptivePriorityScheduler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retained overlay meshes — pure event-driven mesh store (Batch 3 §10).
 * Rebuilds happen only in response to an InvalidationKey drained from the
 * shared OverlayInvalidationQueue. No polling, no timers, no version
 * comparisons anywhere in this class.
 */
public final class OverlayGpuCache {

    private static final long AGING_PROMOTION_TICKS = 200L;
    private static final int MAX_TRANSFER_PER_DRAIN = 2048;

    private final Map<InvalidationKey, List<OverlayGpuMesh>> meshes = new HashMap<>();
    private final Set<InvalidationKey> queuedForRebuild = new HashSet<>();
    private final Set<InvalidationKey> currentlyBuilding = new HashSet<>();
    private final Map<InvalidationKey, Long> firstQueuedTick = new HashMap<>();
    private final AdaptivePriorityScheduler<InvalidationKey> priorityScheduler = new AdaptivePriorityScheduler<>();

    private long invalidationsProcessed = 0L;
    private long staleDiscards = 0L;
    private long cancelledRebuilds = 0L;
    private long totalRebuildNanos = 0L;
    private long totalQueueLatencyTicks = 0L;
    private long latencySamples = 0L;

    public void drainInvalidations(OverlayInvalidationQueue queue, OverlaySurfaceProvider provider,
                                   OverlaySurfaceStateStore stateStore, ClientLevel level,
                                   float depthOffset, long currentTick, long budgetNanos) {
        int transferred = 0;
        while (transferred < MAX_TRANSFER_PER_DRAIN) {
            var polled = queue.pollHighestPriorityEntry();
            if (polled.isEmpty()) break;

            InvalidationKey key = polled.get().key();
            if (!queuedForRebuild.contains(key) && !currentlyBuilding.contains(key)) {
                queuedForRebuild.add(key);
                firstQueuedTick.put(key, currentTick);
                priorityScheduler.submit(key, agedPriority(key, polled.get().priority(), currentTick));
            }
            transferred++;
        }

        priorityScheduler.drainBudgeted(Integer.MAX_VALUE, budgetNanos, key -> {
            queuedForRebuild.remove(key);
            Long queuedAt = firstQueuedTick.remove(key);

            currentlyBuilding.add(key);
            long buildStart = System.nanoTime();
            rebuild(key, provider, stateStore, level, depthOffset, currentTick);
            totalRebuildNanos += System.nanoTime() - buildStart;
            currentlyBuilding.remove(key);

            if (queuedAt != null) {
                totalQueueLatencyTicks += (currentTick - queuedAt);
                latencySamples++;
            }
            invalidationsProcessed++;
        });
    }

    private int agedPriority(InvalidationKey key, InvalidationPriority priority, long currentTick) {
        Long firstSeen = firstQueuedTick.get(key);
        if (firstSeen != null && currentTick - firstSeen >= AGING_PROMOTION_TICKS) return 0;
        return priority.schedulerWeight();
    }

    private void rebuild(InvalidationKey key, OverlaySurfaceProvider provider, OverlaySurfaceStateStore stateStore,
                         ClientLevel level, float depthOffset, long currentTick) {
        long generationAtBuild = provider.chunkGeneration(key.chunkPos());
        if (generationAtBuild < 0) {
            cancelledRebuilds++;
            closeAndRemove(key);
            return;
        }

        List<OverlaySurfaceQuad> quads = provider.quadsFor(key.chunkPos(), key.face(), level);
        OverlayMeshBuilder.ChunkFaceMeshes classified =
                OverlayMeshBuilder.classify(quads, stateStore, key.type(), currentTick, level);

        if (provider.chunkGeneration(key.chunkPos()) != generationAtBuild) {
            staleDiscards++;
            return;
        }

        List<OverlayGpuMesh> existing = meshes.getOrDefault(key, List.of());
        Map<Integer, OverlayGpuMesh> existingByLevel = new HashMap<>();
        for (OverlayGpuMesh mesh : existing) existingByLevel.put(mesh.level(), mesh);

        List<OverlayGpuMesh> rebuilt = new ArrayList<>();
        for (Map.Entry<Integer, List<OverlaySurfaceQuad>> entry : classified.quadsByLevel().entrySet()) {
            int lvl = entry.getKey();
            OverlayVisualProfile profile = OverlayVisualRegistry.resolve(key.type(), lvl);
            var vertexBuffer = OverlayBufferManager.upload(entry.getValue(), profile, depthOffset);

            OverlayGpuMesh mesh = existingByLevel.getOrDefault(lvl, new OverlayGpuMesh(key.chunkPos(), key.face(), lvl));
            if (vertexBuffer != null) {
                mesh.assign(vertexBuffer, entry.getValue().size(), generationAtBuild, currentTick);
                rebuilt.add(mesh);
            } else {
                mesh.clearEmpty(generationAtBuild, currentTick);
            }
        }

        for (OverlayGpuMesh mesh : existing) {
            if (!classified.quadsByLevel().containsKey(mesh.level())) mesh.close();
        }

        if (rebuilt.isEmpty()) meshes.remove(key); else meshes.put(key, rebuilt);
    }

    public List<OverlayGpuMesh> meshesFor(ChunkPos chunkPos, OverlayType type, Direction face) {
        return meshes.getOrDefault(new InvalidationKey(chunkPos, type, face), List.of());
    }

    public Iterable<Map.Entry<InvalidationKey, List<OverlayGpuMesh>>> allMeshEntries() {
        return meshes.entrySet();
    }

    public void invalidateAll(OverlayInvalidationQueue queue) {
        for (InvalidationKey key : meshes.keySet()) {
            queue.enqueue(key, InvalidationPriority.BACKGROUND);
        }
    }

    public void onChunkUnload(ChunkPos chunkPos) {
        Iterator<Map.Entry<InvalidationKey, List<OverlayGpuMesh>>> it = meshes.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().chunkPos().equals(chunkPos)) {
                for (OverlayGpuMesh mesh : e.getValue()) mesh.close();
                it.remove();
            }
        }
        queuedForRebuild.removeIf(k -> k.chunkPos().equals(chunkPos));
        currentlyBuilding.removeIf(k -> k.chunkPos().equals(chunkPos));
        firstQueuedTick.keySet().removeIf(k -> k.chunkPos().equals(chunkPos));
    }

    private void closeAndRemove(InvalidationKey key) {
        List<OverlayGpuMesh> existing = meshes.remove(key);
        if (existing != null) for (OverlayGpuMesh mesh : existing) mesh.close();
    }

    public void reset() {
        for (List<OverlayGpuMesh> list : meshes.values()) for (OverlayGpuMesh mesh : list) mesh.close();
        meshes.clear();
        queuedForRebuild.clear();
        currentlyBuilding.clear();
        firstQueuedTick.clear();
        priorityScheduler.reset();
        invalidationsProcessed = 0L;
        staleDiscards = 0L;
        cancelledRebuilds = 0L;
        totalRebuildNanos = 0L;
        totalQueueLatencyTicks = 0L;
        latencySamples = 0L;
    }

    public int cachedMeshCount() {
        int total = 0;
        for (List<OverlayGpuMesh> list : meshes.values()) total += list.size();
        return total;
    }

    public int queuedForRebuildCount()   { return queuedForRebuild.size(); }
    public int currentlyBuildingCount()  { return currentlyBuilding.size(); }
    public long invalidationsProcessed() { return invalidationsProcessed; }
    public long staleDiscards()          { return staleDiscards; }
    public long cancelledRebuilds()      { return cancelledRebuilds; }

    public float averageRebuildNanos() {
        return invalidationsProcessed == 0 ? 0f : (float) totalRebuildNanos / invalidationsProcessed;
    }

    public float averageQueueLatencyTicks() {
        return latencySamples == 0 ? 0f : (float) totalQueueLatencyTicks / latencySamples;
    }
}