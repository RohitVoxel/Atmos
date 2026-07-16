package net.atmos.memory;

import net.atmos.cellgrid.CellCoord;

/**
 * Identity key for one cell's outstanding persistence-layer tracking entry
 * (write-in-flight or load-in-flight). A dimension is included because
 * {@link CellCoord} alone is only unique within a single dimension —
 * different dimensions reuse the same coordinate space.
 */
record CellMemoryKey(String dimensionKey, CellCoord coord) {}