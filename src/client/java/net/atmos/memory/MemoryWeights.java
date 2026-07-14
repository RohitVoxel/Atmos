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
}