package net.atmos.cellgrid;

/**
 * Immutable directional terrain-visibility profile for one Atmospheric Cell.
 *
 * Per Appendix F §3 (SunReach Computational Contract): the Cell Grid owns
 * generation and storage of Horizon Maps. It does NOT evaluate them against
 * the current sun direction — that runtime evaluation belongs entirely to the
 * future SunReach System (Chapter 8), which is not yet implemented.
 *
 * A Horizon Map divides the full circle around a cell's center into a fixed
 * number of angular sectors. Each sector stores the maximum elevation angle
 * (radians, measured from the cell's horizontal plane) at which terrain
 * blocks the sky in that direction. A future SunReach evaluation can compare
 * the current sun elevation angle against the sector nearest the sun's
 * azimuth in O(1) — no ray marching, no block scanning at runtime
 * (Appendix D §3: "GPU ray marching remains prohibited", "no voxel ray
 * traversal is permitted during runtime").
 *
 * Immutability: once constructed, a HorizonMap never changes. Regeneration
 * (Appendix D §6 / Appendix F §7) always produces a brand new HorizonMap
 * instance that replaces the old one on the owning AtmosCell — it never
 * mutates sector values in place. This makes the map safe to hand out as a
 * read-only reference to any future consumer without defensive copying on
 * every read.
 */
public final class HorizonMap {

    /** Number of angular sectors dividing the full 360° circle. */
    public static final int SECTOR_COUNT = 8;

    private static final float SECTOR_ANGLE = (float) (2.0 * Math.PI / SECTOR_COUNT);

    private final float[] sectorElevationAngles;

    public HorizonMap(float[] sectorElevationAngles) {
        if (sectorElevationAngles.length != SECTOR_COUNT) {
            throw new IllegalArgumentException(
                    "HorizonMap requires exactly " + SECTOR_COUNT + " sectors, got "
                            + sectorElevationAngles.length);
        }
        // Defensive copy on construction — the array reference passed in by
        // the generator must not be externally mutable after this point.
        this.sectorElevationAngles = sectorElevationAngles.clone();
    }

    /**
     * Returns the stored blocking elevation angle (radians) for the sector
     * nearest the given azimuth. No interpolation between sectors — a future
     * SunReach evaluator may choose to interpolate itself; this class only
     * exposes the raw sampled data per the read-only access contract.
     *
     * @param azimuthRadians direction around the horizontal plane, 0 = +X axis,
     *                        increasing counter-clockwise (matches Math.atan2 convention).
     */
    public float elevationAngleAt(float azimuthRadians) {
        float normalized = azimuthRadians % (float) (2.0 * Math.PI);
        if (normalized < 0f) normalized += (float) (2.0 * Math.PI);
        int sector = (int) (normalized / SECTOR_ANGLE) % SECTOR_COUNT;
        return sectorElevationAngles[sector];
    }

    /** Read-only copy of all sector values, for future debug overlays only. */
    public float[] sectorsView() {
        return sectorElevationAngles.clone();
    }
}