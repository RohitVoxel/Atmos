package net.atmos.overlay;

import net.atmos.config.AtmosConfig;
import net.atmos.scheduling.AdaptivePriorityScheduler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OverlayChunkSurfaceCache implements OverlaySurfaceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Overlay");

    private final Map<Long, ChunkSurfaceIndex> chunkIndices = new HashMap<>();
    private final Map<Long, ChunkLifecycleState> lifecycle = new HashMap<>();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private final OverlaySurfaceStateStore stateStore;
    private final OverlayInvalidationQueue invalidationQueue;
    private final Map<Long, Long> nextSimulationTick = new HashMap<>();

    private final OverlayDirtyEventFilter dirtyEventFilter = new OverlayDirtyEventFilter();
    private final OverlayTickBatchCollector tickBatchCollector = new OverlayTickBatchCollector(dirtyEventFilter);
    private final AdaptivePriorityScheduler<Long> pendingPositions = new AdaptivePriorityScheduler<>();

    private static final int STABILIZED_POSITION_PRIORITY = 0;
    private static final int VISIBLE_NEAR_RADIUS_CHUNKS = 6;
    private static final int LOADED_NEAR_RADIUS_CHUNKS = 12;
    private static final int LOADED_OTHER_RADIUS_CHUNKS = 20;

    private ChunkPos viewerChunk = null;

    private long chunksFullyRebuilt = 0L;
    private long incrementalUpdatesPerformed = 0L;
    private long totalFullRebuildNanos = 0L;
    private long totalIncrementalNanos = 0L;
    private long lastFullRebuildNanos = 0L;
    private int lastDirtyBatchSize = 0;
    private int lastDirtyQueueRemaining = 0;

    public OverlayChunkSurfaceCache(OverlayInvalidationQueue invalidationQueue,
                                    OverlayLevelCrossingScheduler<SurfaceTransitionKey> levelCrossingScheduler) {
        this.invalidationQueue = invalidationQueue;
        this.stateStore = new OverlaySurfaceStateStore(levelCrossingScheduler, invalidationQueue);
    }

    public OverlaySurfaceStateStore getStateStore() { return stateStore; }
    public OverlayTickBatchCollector getTickBatchCollector() { return tickBatchCollector; }

    public void updateViewerChunk(ChunkPos chunkPos) {
        this.viewerChunk = chunkPos;
    }

    /** Progressive scanning (Batch 3 §6) — never scans synchronously here. Duplicate-load guarded. */
    public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        if (lifecycle.get(key) == ChunkLifecycleState.WAITING_FOR_SCAN) return;
        setLifecycle(key, ChunkLifecycleState.WAITING_FOR_SCAN);
        scanQueue.add(key);
    }

    public void onChunkUnload(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long key = chunkPos.toLong();
        setLifecycle(key, ChunkLifecycleState.UNLOADING);
        scanQueue.remove(key);
        chunkIndices.remove(key);
        nextSimulationTick.remove(key);
        lifecycle.remove(key);
        invalidationQueue.cancelChunk(chunkPos);
        stateStore.cancelChunk(chunkPos);
    }

    public void markDirty(BlockPos pos) {
        tickBatchCollector.record(pos.asLong());
    }

    /** Scan budget first, then dirty-position batch, then due level crossings — in that priority order. */
    public void processDirty(ClientLevel level, int dirtyBudget, long currentTick) {
        processScanQueue(level, AtmosConfig.get().overlay.safeChunkScanBudget());

        for (Long packedPos : tickBatchCollector.drainTick()) {
            pendingPositions.submit(packedPos, STABILIZED_POSITION_PRIORITY);
        }

        if (pendingPositions.pendingCount() == 0) {
            lastDirtyBatchSize = 0;
            lastDirtyQueueRemaining = 0;
        } else {
            long budgetNanos = AtmosConfig.get().overlay.safeBackgroundWorkerBudgetNanos();
            int processed = pendingPositions.drainBudgeted(dirtyBudget, budgetNanos, packedPos -> {
                long start = System.nanoTime();
                applyIncrementalUpdate(level, BlockPos.of(packedPos));
                incrementalUpdatesPerformed++;
                totalIncrementalNanos += System.nanoTime() - start;
            });
            lastDirtyBatchSize = processed;
            lastDirtyQueueRemaining = pendingPositions.pendingCount();
        }

        stateStore.pumpLevelCrossings(currentTick, this::priorityForChunk);
    }

    private void processScanQueue(ClientLevel level, int budget) {
        int processed = 0;
        while (processed < budget && !scanQueue.isEmpty()) {
            long key = scanQueue.poll();
            ChunkPos chunkPos = new ChunkPos(key);

            if (!level.hasChunk(chunkPos.x, chunkPos.z) || lifecycle.get(key) == ChunkLifecycleState.UNLOADING) {
                lifecycle.remove(key);
                continue;
            }

            setLifecycle(key, ChunkLifecycleState.SCANNING);
            rebuildFull(level, chunkPos);
            setLifecycle(key, ChunkLifecycleState.READY);
            processed++;
        }
    }

    private void applyIncrementalUpdate(ClientLevel level, BlockPos changedPos) {
        updateAffectedPosition(level, changedPos);
        for (Direction dir : Direction.values()) {
            updateAffectedPosition(level, changedPos.relative(dir));
        }
    }

    private void updateAffectedPosition(ClientLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        long key = chunkPos.toLong();
        ChunkSurfaceIndex index = chunkIndices.get(key);
        if (index == null) return;

        setLifecycle(key, ChunkLifecycleState.DIRTY);
        index.updateSinglePosition(level, pos);
        setLifecycle(key, ChunkLifecycleState.READY);
    }

    private void rebuildFull(ClientLevel level, ChunkPos chunkPos) {
        long start = System.nanoTime();

        ChunkSurfaceIndex index = new ChunkSurfaceIndex(chunkPos, stateStore::clear, this::onSurfaceInvalidated);
        index.rebuildFull(level);
        chunkIndices.put(chunkPos.toLong(), index);

        chunksFullyRebuilt++;
        lastFullRebuildNanos = System.nanoTime() - start;
        totalFullRebuildNanos += lastFullRebuildNanos;
    }

    private void onSurfaceInvalidated(ChunkPos chunkPos, Direction face, long generation, InvalidationReason reason) {
        InvalidationPriority priority = priorityForChunk(chunkPos);
        for (OverlayType type : OverlayType.values()) {
            if (face == Direction.DOWN && !type.supportsUnderside()) continue;
            invalidationQueue.enqueue(new InvalidationKey(chunkPos, type, face), priority);
        }
    }

    private InvalidationPriority priorityForChunk(ChunkPos chunkPos) {
        if (viewerChunk == null) return InvalidationPriority.LOADED_OTHER;
        int distance = Math.max(Math.abs(chunkPos.x - viewerChunk.x), Math.abs(chunkPos.z - viewerChunk.z));
        if (distance <= VISIBLE_NEAR_RADIUS_CHUNKS) return InvalidationPriority.VISIBLE_NEAR;
        if (distance <= LOADED_NEAR_RADIUS_CHUNKS) return InvalidationPriority.LOADED_NEAR;
        if (distance <= LOADED_OTHER_RADIUS_CHUNKS) return InvalidationPriority.LOADED_OTHER;
        return InvalidationPriority.BACKGROUND;
    }

    /** Explicit user command (/atmos debug overlay rebuild) — immediate, bypasses the scan budget. */
    public void rebuildAll(ClientLevel level) {
        for (Long key : List.copyOf(chunkIndices.keySet())) {
            ChunkPos chunkPos = new ChunkPos(key);
            if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                scanQueue.remove(key);
                setLifecycle(key, ChunkLifecycleState.SCANNING);
                rebuildFull(level, chunkPos);
                setLifecycle(key, ChunkLifecycleState.READY);
            } else {
                chunkIndices.remove(key);
                lifecycle.remove(key);
            }
        }
    }

    public void simulate(OverlayAccumulationSimulation simulation, OverlayEnvironmentalContext ctx,
                         ChunkPos playerChunk, int simulationRadiusChunks, long currentTick) {
        for (Map.Entry<Long, ChunkSurfaceIndex> e : chunkIndices.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(e.getKey());
            int interval = OverlaySimulationScheduler.intervalTicksFor(chunkPos, playerChunk, simulationRadiusChunks);
            if (interval < 0) continue;

            long due = nextSimulationTick.getOrDefault(e.getKey(), 0L);
            if (currentTick < due) continue;
            nextSimulationTick.put(e.getKey(), currentTick + interval);

            e.getValue().advanceSimulation(simulation, ctx, currentTick);
        }
    }

    @Override
    public List<OverlaySurfaceQuad> quadsFor(ChunkPos chunkPos, Direction face, ClientLevel level) {
        ChunkSurfaceIndex index = chunkIndices.get(chunkPos.toLong());
        return index == null ? List.of() : index.mergedQuads(face, level);
    }

    @Override
    public long chunkGeneration(ChunkPos chunkPos) {
        ChunkSurfaceIndex index = chunkIndices.get(chunkPos.toLong());
        return index == null ? -1L : index.generation();
    }

    private void setLifecycle(long key, ChunkLifecycleState next) {
        ChunkLifecycleState current = lifecycle.getOrDefault(key, ChunkLifecycleState.UNSCANNED);
        if (!current.canTransitionTo(next)) {
            LOGGER.debug("Atmos: unexpected overlay chunk lifecycle transition {} -> {} for {}",
                    current, next, new ChunkPos(key));
        }
        lifecycle.put(key, next);
    }

    public ChunkLifecycleState stateOf(ChunkPos chunkPos) {
        return lifecycle.getOrDefault(chunkPos.toLong(), ChunkLifecycleState.UNSCANNED);
    }

    public Map<ChunkLifecycleState, Integer> lifecycleCounts() {
        Map<ChunkLifecycleState, Integer> counts = new EnumMap<>(ChunkLifecycleState.class);
        for (ChunkLifecycleState state : lifecycle.values()) {
            counts.merge(state, 1, Integer::sum);
        }
        return counts;
    }

    public int scanQueueSize() { return scanQueue.size(); }
    public int cachedChunkCount() { return chunkIndices.size(); }

    public int cachedSurfaceCount() {
        int total = 0;
        for (ChunkSurfaceIndex index : chunkIndices.values()) total += index.rawFaceCount();
        return total;
    }

    public int cachedPositionCount() {
        int total = 0;
        for (ChunkSurfaceIndex index : chunkIndices.values()) total += index.cachedPositionCount();
        return total;
    }

    public int mergedSurfaceCount() {
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return 0;
        int total = 0;
        for (ChunkSurfaceIndex index : chunkIndices.values()) total += index.mergedQuads(Direction.UP, level).size();
        return total;
    }

    public int largestMergedQuadArea() {
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return 0;
        int largest = 0;
        for (ChunkSurfaceIndex index : chunkIndices.values()) {
            for (OverlaySurfaceQuad quad : index.mergedQuads(Direction.UP, level)) {
                int area = quad.extentA() * quad.extentB();
                if (area > largest) largest = area;
            }
        }
        return largest;
    }

    public long estimatedMemoryBytes() {
        return (long) cachedSurfaceCount() * 64L;
    }

    public int dirtyQueueSize() { return pendingPositions.pendingCount(); }
    public int lastDirtyBatchSize() { return lastDirtyBatchSize; }
    public int lastDirtyQueueRemaining() { return lastDirtyQueueRemaining; }
    public long chunksRebuilt() { return chunksFullyRebuilt; }
    public long incrementalUpdatesPerformed() { return incrementalUpdatesPerformed; }
    public long lastChunkRebuildNanos() { return lastFullRebuildNanos; }

    public float averageFullRebuildNanos() {
        return chunksFullyRebuilt == 0 ? 0f : (float) totalFullRebuildNanos / chunksFullyRebuilt;
    }

    public float averageIncrementalNanos() {
        return incrementalUpdatesPerformed == 0 ? 0f : (float) totalIncrementalNanos / incrementalUpdatesPerformed;
    }

    public void reset() {
        chunkIndices.clear();
        lifecycle.clear();
        scanQueue.clear();
        tickBatchCollector.reset();
        pendingPositions.reset();
        stateStore.reset();
        nextSimulationTick.clear();
        viewerChunk = null;

        chunksFullyRebuilt = 0L;
        incrementalUpdatesPerformed = 0L;
        totalFullRebuildNanos = 0L;
        totalIncrementalNanos = 0L;
        lastFullRebuildNanos = 0L;
        lastDirtyBatchSize = 0;
        lastDirtyQueueRemaining = 0;
    }
}