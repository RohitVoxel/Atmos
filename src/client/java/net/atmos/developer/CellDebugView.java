package net.atmos.developer;

/**
 * Immutable snapshot of cell data for visualization.
 * Ensures overlays never depend on production AtmosCell internals.
 */
public record CellDebugView(
        float centerX,
        float centerY,
        float centerZ,
        float humidity,
        boolean skyExposed
) {}