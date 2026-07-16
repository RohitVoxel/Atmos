package net.atmos.memory;

import net.atmos.cellgrid.CellCoord;

/**
 * Immutable Copy-on-Enqueue serialization snapshot of one Atmospheric
 * Cell's Historical Data, per Appendix F 2.0 §2.1 / §13.12–§13.13.
 *
 * Captured once, synchronously on the Simulation Thread, at the exact
 * moment a cell's Historical Data leaves Simulation Thread ownership
 * (cache eviction or session flush — see CellGrid). The Background IO
 * layer never touches the live AtmosCell; it only ever reads this
 * immutable copy, satisfying the Copy-on-Enqueue contract exactly.
 */
public record CellMemorySnapshot(
        String dimensionKey,
        CellCoord coord,
        float humidityMemory,
        float stormInfluence,
        long lastMemoryUpdateTick
) {
    public CellMemorySnapshot {
        if (dimensionKey == null || dimensionKey.isEmpty()) {
            throw new IllegalArgumentException("dimensionKey must not be null or empty");
        }
        if (coord == null) {
            throw new IllegalArgumentException("coord must not be null");
        }
    }
}