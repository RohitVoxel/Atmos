package net.atmos.memory;

/**
 * Centralized tuning constants for Chapter 13 Atmospheric Memory.
 * No Memory class may declare its own tuning constant.
 *
 * No decay rate is numerically anchored anywhere in the Master Guide —
 * only the qualitative classification "Extremely Slow" (Ch.3 §7, Ch.5 §11),
 * slower than EnvironmentalState's own humidity/storm drifters (accel
 * 0.6/0.55, damp 3.5/2.2). Implementation-defined, same status as every
 * other unanchored transfer function in this codebase.
 */
public final class MemoryWeights {

    private MemoryWeights() {}

    // Stage 1 — global channels (AtmosphericMemoryState)
    public static final float GLOBAL_HUMIDITY_MEMORY_ACCEL   = 0.18f;
    public static final float GLOBAL_HUMIDITY_MEMORY_DAMP    = 1.4f;
    public static final float GLOBAL_STORM_MEMORY_ACCEL      = 0.15f;
    public static final float GLOBAL_STORM_MEMORY_DAMP       = 1.1f;
    public static final float GLOBAL_HUMIDITY_MEMORY_DEFAULT = 0.35f;
    public static final float GLOBAL_STORM_MEMORY_DEFAULT    = 0.0f;

    // Stage 2 — per-cell Historical Memory (AtmosCell)
    // Kept as separate constants from the GLOBAL_* set (even though
    // currently equal) so the two scopes can be tuned independently later
    // without implying they must always match.
    public static final float CELL_HUMIDITY_MEMORY_ACCEL   = 0.18f;
    public static final float CELL_HUMIDITY_MEMORY_DAMP    = 1.4f;
    public static final float CELL_STORM_INFLUENCE_ACCEL   = 0.15f;
    public static final float CELL_STORM_INFLUENCE_DAMP    = 1.1f;
    public static final float CELL_HUMIDITY_MEMORY_DEFAULT = 0.35f;
    public static final float CELL_STORM_INFLUENCE_DEFAULT = 0.0f;

    // --- Stage 3 — Asynchronous Persistence (Appendix F 2.0 §13.16, Appendix D §5) ---

    /** Appendix D §5's own documented example capacity ("e.g., 64 cells"). Steady-state eviction traffic only — bulk flush uses a single batched task and never contends for this. */
    public static final int PERSISTENCE_QUEUE_CAPACITY = 64;

    /**
     * Audit-fix cap (Finding G) on {@code loadResults} — bounds the count
     * of completed-but-unclaimed disk reads (orphaned when a cell is
     * evicted from cache before its async load finishes). No numeric
     * anchor exists in the Guide for this; sized with headroom above the
     * write queue since reads are cheaper and more frequent than writes.
     * Dropping an entry here loses nothing permanently — the source file
     * on disk is untouched, so a later revisit simply re-requests it.
     */
    public static final int LOAD_RESULTS_CAPACITY = 128;

    /**
     * Audit-fix (Finding F) bounded grace period given to the background
     * IO thread at genuine session teardown (disconnect only, never
     * dimension change) before it is forcibly stopped. Implementation-
     * defined — no numeric anchor exists in the Guide for shutdown grace.
     */
    public static final long PERSISTENCE_SHUTDOWN_GRACE_MS = 2000L;

    // --- Stage 4 — Adaptive Performance cadence scaling (§13.18) ---
    // No numeric anchor exists in Chapter 13 for update-interval bounds —
    // implementation-defined, same status as every other unanchored
    // transfer function in this codebase.

    /** budget = 1.0 -> advance every call (no accumulation wait). */
    public static final float MEMORY_UPDATE_INTERVAL_MIN_SEC = 0.0f;

    /** budget = 0.0 -> advance at most once per second. */
    public static final float MEMORY_UPDATE_INTERVAL_MAX_SEC = 1.0f;
}