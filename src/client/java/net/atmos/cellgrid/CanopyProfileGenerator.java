package net.atmos.cellgrid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates a CanopyProfile by sampling raw per-slab foliage presence in a
 * small, fixed horizontal x vertical grid, searching UPWARD from each
 * column's reference Y, using CanopyProfile.sampleFraction()'s shared
 * placement schedule.
 *
 * Vertical placement uses CanopyProfile.sampleFraction(v) rather than a
 * linear fraction — see CanopyProfile's class doc for the full rationale.
 *
 * BlockPos allocation: a single BlockPos.MutableBlockPos is reused across
 * all SAMPLE_COUNT * VERTICAL_SAMPLE_COUNT = 40 probes in one generate()
 * call. It is passed only as a method parameter, mutated via .set()
 * immediately before each synchronous getBlockState() call, and never
 * stored, returned, or captured — CanopyProfile only ever receives the
 * resulting boolean[], never the position object. No reference escapes;
 * no stale-read is possible since every read happens strictly after its
 * own .set() call. Thread safety follows directly from Appendix D's
 * existing Simulation-Thread-only constraint on Cell Grid generation — no
 * concurrent access to this instance is possible. MutableBlockPos is
 * Minecraft's own built-in reuse primitive, not a new mechanism.
 *
 * Leaf classification deliberately ignores every BlockState property
 * LeavesBlock carries. `persistent` and `distance` are decay-mechanic
 * bookkeeping with zero visual meaning; `waterlogged` describes submersion
 * state, not foliage density — underwater light transmission is a
 * distinct, out-of-scope future concern. Vanilla itself renders every leaf
 * species and state as a uniform, full-coverage visual element, so
 * treating them equally is a faithful read of the source data model.
 *
 * Horizontal offset (HORIZONTAL_PROBE_OFFSET) remains a fixed physical
 * constant, independent of CellGrid.CELL_SIZE.
 *
 * Cost: 40 fixed getBlockState calls per cell generation/regeneration —
 * bounded, deterministic, O(1), independent of world height.
 *
 * Known limitation: this generator performs direct point sampling, not
 * line-of-sight ray marching (which the architecture forbids). A cave cell
 * with solid rock overhead and forest canopy further above may therefore
 * register a spurious leaf hit above the rock — see
 * CanopyOcclusionEvaluator's class doc for why this is architecturally
 * safe without requiring this generator to become terrain-aware.
 *
 * Pure, stateless generator. Invoked by {@link CellGrid} on cell creation
 * and regeneration (Appendix ZD §5), storing the result on {@link AtmosCell}.
 */
final class CanopyProfileGenerator {

    private CanopyProfileGenerator() {}

    private static final int HORIZONTAL_PROBE_OFFSET = 4;

    static CanopyProfile generate(CellCoord coord, int cellSize, ClientLevel level) {
        int centerX = coord.centerWorldX(cellSize);
        int centerY = coord.centerWorldY(cellSize);
        int centerZ = coord.centerWorldZ(cellSize);

        int[][] sampleOffsets = {
                {0, 0},
                {HORIZONTAL_PROBE_OFFSET, 0}, {-HORIZONTAL_PROBE_OFFSET, 0},
                {0, HORIZONTAL_PROBE_OFFSET}, {0, -HORIZONTAL_PROBE_OFFSET}
        };

        boolean[][] hits = new boolean[CanopyProfile.SAMPLE_COUNT][];

        // Single reused mutable position across all 40 probes in this call
        // — one allocation instead of forty. See class doc for the
        // reference-escape and thread-safety verification.
        BlockPos.MutableBlockPos probePos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < sampleOffsets.length; i++) {
            int sampleX = centerX + sampleOffsets[i][0];
            int sampleZ = centerZ + sampleOffsets[i][1];

            hits[i] = sampleColumnHits(level, sampleX, centerY, sampleZ, probePos);
        }

        return new CanopyProfile(hits);
    }

    /**
     * Samples CanopyProfile.VERTICAL_SAMPLE_COUNT positions from
     * {@code baseY} upward through CanopyProfile.SEARCH_HEIGHT_BLOCKS,
     * using CanopyProfile.sampleFraction() for placement, clamped to the
     * level's buildable height range, returning per-slab foliage presence
     * ordered nearest-to-farthest.
     *
     * Boundary note: if the clamped range is narrower than the nominal
     * SEARCH_HEIGHT_BLOCKS window, the biased fraction is rescaled into
     * whatever range is actually available. Accepted, documented boundary
     * simplification, consistent with HorizonMapGenerator/ValleyFogModifier.
     */
    private static boolean[] sampleColumnHits(ClientLevel level, int x, int baseY, int z,
                                              BlockPos.MutableBlockPos probePos) {
        boolean[] hits = new boolean[CanopyProfile.VERTICAL_SAMPLE_COUNT];

        int minY = baseY;
        int maxY = baseY + (int) CanopyProfile.SEARCH_HEIGHT_BLOCKS;

        int clampedMin = Math.max(minY, level.getMinBuildHeight());
        int clampedMax = Math.min(maxY, level.getMaxBuildHeight() - 1);

        if (clampedMax < clampedMin) {
            return hits; // all-false — cell entirely outside buildable range
        }

        int range = clampedMax - clampedMin;

        for (int v = 0; v < CanopyProfile.VERTICAL_SAMPLE_COUNT; v++) {
            float biasedFraction = CanopyProfile.sampleFraction(v);
            int sampleY = clampedMin + Math.round(biasedFraction * range);

            probePos.set(x, sampleY, z);
            BlockState state = level.getBlockState(probePos);
            hits[v] = isFoliage(state);
        }

        return hits;
    }

    /**
     * Single, isolated classification point for "counts as canopy for
     * Stage Four purposes." instanceof LeavesBlock correctly covers every
     * vanilla leaf type and any modded leaf block extending it.
     */
    private static boolean isFoliage(BlockState state) {
        return state.getBlock() instanceof LeavesBlock;
    }
}