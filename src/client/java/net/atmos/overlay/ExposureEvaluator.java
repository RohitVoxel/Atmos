package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Continuous [0,1] environmental exposure for one exposed block face.
 * Replaces the previous boolean skyVisible signal. No numeric formula for
 * this value exists anywhere in the Atmos architecture — this is an
 * implementation-defined heuristic, deliberately isolated from the
 * scanning/cache path so future consumers (snow accumulation, dust, ash,
 * moss, ice, wall streaking) can refine it without touching ChunkSurfaceIndex.
 *
 * 1.0 = fully open to sky and weather. 0.0 = fully enclosed.
 */
final class ExposureEvaluator {

    private static final int CANOPY_PROBES = 6;
    private static final int CAVE_DEPTH_THRESHOLD = 40;

    private ExposureEvaluator() {}

    static float evaluate(ClientLevel level, BlockPos pos, Direction face, boolean skyVisible) {
        if (face != Direction.UP) {
            // Side/down faces follow the block's own sky access rather than
            // a per-direction probe — a canopy overhead shelters every face
            // of the block beneath it, not only its top.
            return skyVisible ? 0.85f : shelteredExposure(level, pos);
        }

        if (skyVisible) {
            int canopyHits = 0;
            for (int i = 1; i <= CANOPY_PROBES; i++) {
                BlockState above = level.getBlockState(pos.above(i));
                if (!above.isAir() && above.getFluidState().isEmpty()) canopyHits++;
            }
            return FogMath.clamp(1.0f - (canopyHits / (float) CANOPY_PROBES) * 0.6f, 0.4f, 1.0f);
        }

        return shelteredExposure(level, pos);
    }

    private static float shelteredExposure(ClientLevel level, BlockPos pos) {
        int openNeighbors = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.canSeeSky(pos.relative(dir).above())) openNeighbors++;
        }
        if (openNeighbors > 0) {
            return FogMath.lerp(0.15f, 0.45f, openNeighbors / 4.0f);
        }
        return pos.getY() < level.getMinBuildHeight() + CAVE_DEPTH_THRESHOLD ? 0.0f : 0.10f;
    }
}