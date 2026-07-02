package net.atmos.cellgrid;

/**
 * Discrete 3D coordinate identifying one Atmospheric Cell within the Cell Grid.
 *
 * Per Chapter 6 §7 ("World Space vs Cell Space"), world-space block coordinates
 * are converted into cell-space coordinates by integer division against the
 * configured cell size. Every world position inside the same CELL_SIZE^3 volume
 * maps to the same CellCoord, which is the basis for the Cell Grid's spatial
 * stability (Chapter 6 §8).
 *
 * This is a pure value type — no behavior beyond coordinate math. It carries
 * no simulation state and is safe to use as a hash-map key (records provide
 * structural equals/hashCode automatically).
 */
public record CellCoord(int x, int y, int z) {

    /**
     * Converts a world-space block position into cell-space coordinates.
     * Uses Math.floorDiv (not integer truncation) so negative world coordinates
     * map to the correct cell rather than rounding toward zero.
     */
    public static CellCoord fromWorld(int worldX, int worldY, int worldZ, int cellSize) {
        return new CellCoord(
                Math.floorDiv(worldX, cellSize),
                Math.floorDiv(worldY, cellSize),
                Math.floorDiv(worldZ, cellSize)
        );
    }

    /** World-space coordinates of this cell's volumetric center, for sampling/generation. */
    public int centerWorldX(int cellSize) { return x * cellSize + cellSize / 2; }
    public int centerWorldY(int cellSize) { return y * cellSize + cellSize / 2; }
    public int centerWorldZ(int cellSize) { return z * cellSize + cellSize / 2; }
}