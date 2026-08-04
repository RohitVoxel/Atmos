package net.atmos.overlay;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Explicit per-chunk overlay lifecycle — Batch 3 §3/§4.
 *
 * canTransitionTo() enumerates every transition this codebase's own logic
 * actually performs (verified by tracing every setLifecycle() call site in
 * OverlayChunkSurfaceCache), including READY -> SCANNING for the explicit
 * /atmos debug overlay rebuild command — a real, intentional pathway, not
 * an omission from the documented UNSCANNED->...->UNLOADING chain.
 */
public enum ChunkLifecycleState {
    UNSCANNED,
    WAITING_FOR_SCAN,
    SCANNING,
    READY,
    DIRTY,
    UNLOADING;

    private static final Map<ChunkLifecycleState, EnumSet<ChunkLifecycleState>> ALLOWED =
            new EnumMap<>(ChunkLifecycleState.class);
    static {
        ALLOWED.put(UNSCANNED, EnumSet.of(WAITING_FOR_SCAN));
        ALLOWED.put(WAITING_FOR_SCAN, EnumSet.of(SCANNING, UNLOADING));
        ALLOWED.put(SCANNING, EnumSet.of(READY, UNLOADING));
        ALLOWED.put(READY, EnumSet.of(DIRTY, SCANNING, UNLOADING));
        ALLOWED.put(DIRTY, EnumSet.of(READY, UNLOADING));
        ALLOWED.put(UNLOADING, EnumSet.noneOf(ChunkLifecycleState.class));
    }

    public boolean canTransitionTo(ChunkLifecycleState next) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(ChunkLifecycleState.class)).contains(next);
    }
}