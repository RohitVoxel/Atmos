package net.atmos.memory;

/**
 * Read-only snapshot of persistence-layer health — Chapter 13 §13.19.
 *
 * cellsEvicted           — count of cells whose Historical Data left the
 *                           active/cached tier and was enqueued for
 *                           persistence (Appendix D §5, CellGrid's
 *                           removeEldestEntry).
 * corruptedReadsDetected — count of disk reads rejected as invalid
 *                           (magic/version mismatch, non-finite or
 *                           out-of-range payload, or IO failure) —
 *                           Chapter 13 §13.17 recovery statistic.
 * ioThreadActive         — true while the background IO thread has not
 *                           been shut down (Appendix F 2.0 §13.12).
 */
public record MemoryDiagnostics(
        int pendingWrites,
        int queuedWriteTasks,
        long writesCompleted,
        long writesDiscarded,
        int loadsInFlight,
        int loadResultsReady,
        long loadsCompleted,
        long loadsDiscarded,
        long cellsEvicted,
        long corruptedReadsDetected,
        boolean ioThreadActive
) {}