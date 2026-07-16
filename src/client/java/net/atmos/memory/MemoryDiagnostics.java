package net.atmos.memory;

/**
 * Read-only snapshot of persistence-layer health — Chapter 13 §13.19.
 * Data only; no overlay UI exists anywhere in the codebase to render it
 * (matches the existing empty {@code FogDebugOverlay} stub), so this is
 * exposed purely as an accessor for a future debug screen to consume.
 *
 * {@code loadsDiscarded} added alongside the audit-fix that gives load
 * requests the same identifiable, cleanup-safe task shape as writes (see
 * {@code AtmosphericMemoryPersistenceService}'s discard-oldest handler) —
 * a discard path now exists for loads just as it already did for writes.
 */
public record MemoryDiagnostics(
        int pendingWrites,
        int queuedWriteTasks,
        long writesCompleted,
        long writesDiscarded,
        int loadsInFlight,
        int loadResultsReady,
        long loadsCompleted,
        long loadsDiscarded
) {}