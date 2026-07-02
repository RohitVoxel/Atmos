package net.atmos.cellgrid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Generates a HorizonMap for a given cell by sampling the vanilla heightmap
 * in a ring of directions and distances around the cell's world-space center.
 *
 * Technique mirrors ValleyFogModifier's existing heightmap-sampling approach
 * (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)) — reusing
 * an established, already-proven sampling method rather than introducing a
 * new terrain-query mechanism (Permanent Instructions: "Extend Before Creating").
 *
 * Pure, stateless generator — no relationship to rendering or simulation
 * timing. Invoked by CellGrid only:
 *   - once, when a cell is first created (Chapter 6 §13), and
 *   - once, when a cell is lazily regenerated after being marked dirty
 *     (Appendix D §6 — Block Update Cache Invalidation).
 *
 * Cost: SECTOR_COUNT * SAMPLE_DISTANCES.length heightmap queries per cell
 * (8 * 3 = 24 with current constants). CellGrid gates generation behind
 * player-movement thresholds so this never runs every frame.
 */
final class HorizonMapGenerator {

    private HorizonMapGenerator() {}

    // Sample distances in blocks, near to far. The farthest visible
    // obstruction in a direction can still be the true blocking angle
    // (e.g. a distant mountain over a near dip), so all distances are
    // sampled and the maximum elevation angle across them is kept.
    private static final float[] SAMPLE_DISTANCES = {8f, 16f, 32f};

    static HorizonMap generate(CellCoord coord, int cellSize, ClientLevel level) {
        int centerX = coord.centerWorldX(cellSize);
        int centerY = coord.centerWorldY(cellSize);
        int centerZ = coord.centerWorldZ(cellSize);

        float[] sectors = new float[HorizonMap.SECTOR_COUNT];

        for (int s = 0; s < HorizonMap.SECTOR_COUNT; s++) {
            float azimuth = (float) (s * (2.0 * Math.PI / HorizonMap.SECTOR_COUNT));
            float dirX = (float) Math.cos(azimuth);
            float dirZ = (float) Math.sin(azimuth);

            // Default: no obstruction sampled = fully open sky in this direction.
            float maxElevation = -(float) (Math.PI / 2.0);

            for (float distance : SAMPLE_DISTANCES) {
                int sampleX = centerX + Math.round(dirX * distance);
                int sampleZ = centerZ + Math.round(dirZ * distance);

                int terrainHeight = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);

                float heightDelta = terrainHeight - centerY;
                float elevation   = (float) Math.atan2(heightDelta, distance);

                if (elevation > maxElevation) {
                    maxElevation = elevation;
                }
            }

            sectors[s] = maxElevation;
        }

        return new HorizonMap(sectors);
    }
}