package net.atmos.memory;

import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellCoord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous persistence layer for per-cell Historical Data — Appendix
 * F 2.0 §13.12–§13.16, Appendix D §5.
 *
 * Thread isolation (§13.12): all disk IO runs on a single dedicated
 * daemon thread. Every public method here is safe to call from the
 * Simulation Thread and never blocks on IO, with the sole documented
 * exception of {@link #shutdown()}, which is intended only for genuine
 * session teardown (see its own doc).
 *
 * Bounded queue with discard-oldest (Appendix D §5 — "e.g., 64 cells...
 * oldest discarded"; §13.16): implemented via {@link ThreadPoolExecutor}
 * with a bounded {@link ArrayBlockingQueue} and a discard-oldest
 * rejection handler, reusing well-tested JDK machinery. This queue is
 * sized and policed for steady-state per-frame eviction traffic only —
 * bulk flush ({@link #flush}) never submits more than one task
 * regardless of batch size, so it never contends against it (audit
 * Finding E fix).
 *
 * Cancellation / re-entry (§13.15, Appendix F 2.0 "Cancellation Rules"):
 * tracked via {@code inFlight}, keyed by {@link CellMemoryKey}.
 * Cancellation is expressed purely as conditional removal from this map
 * — never as a state transition. {@code PENDING_WRITE -> WRITTEN} is an
 * explicitly prohibited transition (Appendix F 2.0's invalid-transitions
 * table); {@link CellMemoryIoState#WRITTEN} now means, exactly and only,
 * "confirmed persisted" (audit Finding C fix). {@link WriteTask} guards
 * against a write that was cancelled after submission but before it
 * began by checking map identity as its first step.
 *
 * {@code loadResults} is a bounded, insertion-ordered LRU map (audit
 * Finding G fix) — a load that completes after its cell has already been
 * evicted from Cell Grid's own cache entirely is never claimed by
 * {@code drainMemoryLoadResults}; without a bound this accumulates
 * without limit over long sessions. Dropping an unclaimed entry loses
 * nothing permanently: the persisted file is untouched, so a later
 * revisit of that coordinate simply issues a fresh {@link #requestLoad}.
 *
 * Load requests now submit an identifiable {@link LoadTask} rather than
 * a bare lambda (audit starvation fix) — the discard-oldest handler
 * previously only knew how to clean up {@link WriteTask}s; a discarded
 * load lambda's {@code finally} block never ran, permanently stranding
 * that coordinate's {@code loadsInFlight} flag at {@code true}. Both
 * task types are now recognized and cleaned up identically on discard.
 */
public final class AtmosphericMemoryPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Memory");

    private final CellMemoryFileStore fileStore = new CellMemoryFileStore();
    private final ConcurrentHashMap<CellMemoryKey, TrackedCellWrite> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CellMemoryKey, Boolean> loadsInFlight = new ConcurrentHashMap<>();

    /**
     * Bounded, insertion-order LRU (Finding G). Synchronized wrapper is
     * required — unlike every other map here, this one is genuinely
     * written from the IO thread and read/removed from the Simulation
     * Thread with a mutation (removeEldestEntry) that ConcurrentHashMap
     * cannot express. Insertion-order (not access-order): an unclaimed
     * result is most likely orphaned the longer it has sat unclaimed, so
     * the oldest entry is the correct one to drop first.
     */
    private final Map<CellMemoryKey, CellMemorySnapshot> loadResults =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CellMemoryKey, CellMemorySnapshot> eldest) {
                    return size() > MemoryWeights.LOAD_RESULTS_CAPACITY;
                }
            });

    private final AtomicLong writesCompleted = new AtomicLong();
    private final AtomicLong writesDiscarded = new AtomicLong();
    private final AtomicLong loadsCompleted = new AtomicLong();
    private final AtomicLong loadsDiscarded = new AtomicLong();

    private final ThreadPoolExecutor ioExecutor;

    public AtmosphericMemoryPersistenceService() {
        this.ioExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MemoryWeights.PERSISTENCE_QUEUE_CAPACITY),
                runnable -> {
                    Thread t = new Thread(runnable, "Atmos-Memory-IO");
                    t.setDaemon(true);
                    return t;
                },
                this::onRejected
        );
    }

    private final AtomicLong evictionsEnqueued = new AtomicLong();
    /** Enqueues a Copy-on-Enqueue snapshot for background serialization. Never blocks. */
    public void enqueueEviction(String dimensionKey, CellCoord coord,
                                float humidityMemory, float stormInfluence, long lastMemoryUpdateTick) {
        CellMemoryKey key = new CellMemoryKey(dimensionKey, coord);
        CellMemorySnapshot snapshot = new CellMemorySnapshot(
                dimensionKey, coord, humidityMemory, stormInfluence, lastMemoryUpdateTick);
        TrackedCellWrite tracked = new TrackedCellWrite(key, snapshot);
        inFlight.put(key, tracked);
        evictionsEnqueued.incrementAndGet();
        ioExecutor.execute(new WriteTask(tracked));
    }

    /**
     * Player re-entry (§13.15): if a write for this cell is still
     * tracked, returns its snapshot immediately — no disk read needed.
     *
     * Cancellation is conditional map removal only (Finding C fix — see
     * class doc). If {@link WriteTask#run()} has not yet reached its
     * identity check when this runs, the write is skipped entirely. If
     * it has already passed that check, the write proceeds anyway: a
     * harmless redundant disk write of still-valid data captured only
     * moments earlier, never a correctness issue.
     */
    public Optional<CellMemorySnapshot> reclaimPending(String dimensionKey, CellCoord coord) {
        TrackedCellWrite tracked = inFlight.get(new CellMemoryKey(dimensionKey, coord));
        if (tracked == null) return Optional.empty();

        inFlight.remove(tracked.key(), tracked);
        return Optional.of(tracked.snapshot());
    }

    /** Fire-and-forget async load request. Result retrieved later via {@link #pollLoadedResult}. */
    public void requestLoad(String dimensionKey, CellCoord coord) {
        CellMemoryKey key = new CellMemoryKey(dimensionKey, coord);
        if (loadsInFlight.putIfAbsent(key, Boolean.TRUE) != null) return; // already requested

        ioExecutor.execute(new LoadTask(dimensionKey, coord, key));
    }

    /** Non-blocking retrieval of one completed load result, if any. Simulation Thread only. */
    public Optional<CellMemorySnapshot> pollLoadedResult(String dimensionKey, CellCoord coord) {
        return Optional.ofNullable(loadResults.remove(new CellMemoryKey(dimensionKey, coord)));
    }

    /** O(1) fast-path check so callers can skip an otherwise-unnecessary per-cell scan. */
    public boolean hasLoadedResults() {
        return !loadResults.isEmpty();
    }

    /**
     * Best-effort flush of every supplied cell's Historical Data —
     * session/dimension teardown only.
     *
     * Finding E fix: builds one immutable batch and submits it as a
     * single {@link FlushBatchTask}, consuming exactly one slot in the
     * bounded write queue regardless of how many cells are flushed.
     * Previously this called {@link #enqueueEviction} once per cell —
     * for a full active+cached flush (~650 cells against a 64-slot
     * queue) the discard-oldest policy silently dropped the vast
     * majority of those writes. A single batched task never triggers
     * that policy under normal operation.
     */
    public void flush(String dimensionKey, Iterable<AtmosCell> cells) {
        List<CellMemorySnapshot> batch = new ArrayList<>();
        for (AtmosCell cell : cells) {
            batch.add(new CellMemorySnapshot(dimensionKey, cell.coord(),
                    cell.humidityMemory(), cell.stormInfluence(), cell.lastMemoryUpdateTick()));
        }
        if (batch.isEmpty()) return;
        ioExecutor.execute(new FlushBatchTask(List.copyOf(batch)));
    }

    /**
     * Cleanly stops the background IO thread, per Finding F. Allows
     * whatever is already queued — including a {@link #flush}-submitted
     * batch — a bounded grace period to complete before forcibly
     * stopping, rather than leaving persistence to the mercy of the
     * daemon thread being killed abruptly by JVM exit.
     *
     * Intended for genuine session teardown only (disconnect). Never
     * call this for a mid-session dimension change — the caller
     * ({@code CellGrid}) is expected to replace this instance with a
     * fresh one afterward if the client session continues.
     *
     * This is the one method on this class that may block the calling
     * thread, bounded to {@link MemoryWeights#PERSISTENCE_SHUTDOWN_GRACE_MS}.
     * Acceptable specifically because disconnect is already an inherently
     * blocking teardown point in the client (network channel closure,
     * etc.) — never called from a per-frame path.
     */
    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(MemoryWeights.PERSISTENCE_SHUTDOWN_GRACE_MS, TimeUnit.MILLISECONDS)) {
                LOGGER.debug("Atmos: memory persistence shutdown grace period elapsed with work still queued; stopping now");
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public MemoryDiagnostics diagnostics() {
        return new MemoryDiagnostics(
                inFlight.size(),
                ioExecutor.getQueue().size(),
                writesCompleted.get(),
                writesDiscarded.get(),
                loadsInFlight.size(),
                loadResults.size(),
                loadsCompleted.get(),
                loadsDiscarded.get(),
                evictionsEnqueued.get(),
                fileStore.corruptedReadsDetected(),
                !ioExecutor.isShutdown()
        );
    }

    /**
     * Recognizes both task shapes submitted to {@link #ioExecutor} so
     * neither kind of discard leaks tracking state (starvation fix).
     */
    private void onRejected(Runnable runnable, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) return;

        Runnable discarded = executor.getQueue().poll();
        if (discarded instanceof WriteTask task) {
            inFlight.remove(task.tracked.key(), task.tracked);
            writesDiscarded.incrementAndGet();
            LOGGER.debug("Atmos: discarded oldest pending cell-memory write (queue full)");
        } else if (discarded instanceof LoadTask task) {
            loadsInFlight.remove(task.key);
            loadsDiscarded.incrementAndGet();
            LOGGER.debug("Atmos: discarded oldest pending cell-memory load request (queue full)");
        }
        executor.execute(runnable);
    }

    private final class WriteTask implements Runnable {
        private final TrackedCellWrite tracked;

        WriteTask(TrackedCellWrite tracked) {
            this.tracked = tracked;
        }

        @Override
        public void run() {
            // Identity check first (Finding C fix): if reclaimPending()
            // already removed this exact tracked instance from inFlight,
            // this write was cancelled before it began — skip it. This
            // replaces the previous PENDING_WRITE -> WRITTEN cancellation
            // CAS, which misused the terminal "confirmed persisted" state.
            if (inFlight.get(tracked.key()) != tracked) {
                return;
            }
            if (!tracked.compareAndSetState(CellMemoryIoState.PENDING_WRITE, CellMemoryIoState.WRITING)) {
                return; // defensive; unreachable given the identity check above
            }
            fileStore.write(tracked.snapshot());
            tracked.compareAndSetState(CellMemoryIoState.WRITING, CellMemoryIoState.WRITTEN);
            inFlight.remove(tracked.key(), tracked);
            writesCompleted.incrementAndGet();
        }
    }

    private final class LoadTask implements Runnable {
        private final String dimensionKey;
        private final CellCoord coord;
        private final CellMemoryKey key;

        LoadTask(String dimensionKey, CellCoord coord, CellMemoryKey key) {
            this.dimensionKey = dimensionKey;
            this.coord = coord;
            this.key = key;
        }

        @Override
        public void run() {
            try {
                fileStore.read(dimensionKey, coord).ifPresent(snapshot -> {
                    loadResults.put(key, snapshot);
                    loadsCompleted.incrementAndGet();
                });
            } finally {
                loadsInFlight.remove(key);
            }
        }
    }

    private final class FlushBatchTask implements Runnable {
        private final List<CellMemorySnapshot> batch;

        FlushBatchTask(List<CellMemorySnapshot> batch) {
            this.batch = batch;
        }

        @Override
        public void run() {
            for (CellMemorySnapshot snapshot : batch) {
                try {
                    fileStore.write(snapshot);
                    writesCompleted.incrementAndGet();
                } catch (RuntimeException e) {
                    // One bad snapshot must not abort the rest of the batch,
                    // nor silently desync writesCompleted (see class doc).
                    LOGGER.debug("Atmos: flush write failed for {} — {}", snapshot.coord(), e.getMessage());
                }
            }
        }
    }
}