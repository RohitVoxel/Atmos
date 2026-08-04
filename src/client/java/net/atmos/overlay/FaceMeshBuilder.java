package net.atmos.overlay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Generic greedy mesher over a sparse 2D (a,b) grid of exposed faces for a
 * single Direction. Extracted from ChunkSurfaceIndex so face-discovery and
 * mesh construction stay single-responsibility (Atmos File Responsibility
 * rule). Works identically for UP/DOWN (a=X, b=Z) and side faces (one
 * tangent axis + the vertical axis) — the caller supplies the mapping.
 *
 * Only merges cells whose blockState matches and whose exposure is within
 * tolerance. Never merges across block states, environmental buckets, or
 * face directions (one merge call only ever covers one direction).
 */
final class FaceMeshBuilder {

    private static final float EXPOSURE_MERGE_TOLERANCE = 0.05f;

    private FaceMeshBuilder() {}

    record FaceCellData(BlockPos pos, OverlaySurface surface) {}

    static List<OverlaySurfaceQuad> merge(Direction face, int sizeA, int sizeB,
                                          BiFunction<Integer, Integer, FaceCellData> source) {
        boolean[] used = new boolean[sizeA * sizeB];
        List<OverlaySurfaceQuad> quads = new ArrayList<>();

        for (int a = 0; a < sizeA; a++) {
            for (int b = 0; b < sizeB; b++) {
                int idx = a * sizeB + b;
                if (used[idx]) continue;

                FaceCellData origin = source.apply(a, b);
                if (origin == null) continue;

                int extentA = 1;
                while (a + extentA < sizeA && canExtend(source, used, a + extentA, b, sizeB, origin)) {
                    extentA++;
                }

                int extentB = 1;
                outer:
                while (b + extentB < sizeB) {
                    for (int da = 0; da < extentA; da++) {
                        if (!canExtend(source, used, a + da, b + extentB, sizeB, origin)) break outer;
                    }
                    extentB++;
                }

                for (int da = 0; da < extentA; da++) {
                    for (int db = 0; db < extentB; db++) {
                        used[(a + da) * sizeB + (b + db)] = true;
                    }
                }

                OverlaySurface s = origin.surface();
                quads.add(new OverlaySurfaceQuad(
                        origin.pos(), face, extentA, extentB,
                        s.blockState(), s.exposure(), s.temperature(), s.humidity(), s.rainfall(), 0));
            }
        }
        return quads;
    }

    private static boolean canExtend(BiFunction<Integer, Integer, FaceCellData> source, boolean[] used,
                                     int a, int b, int sizeB, FaceCellData origin) {
        int idx = a * sizeB + b;
        if (used[idx]) return false;
        FaceCellData cell = source.apply(a, b);
        if (cell == null) return false;
        return compatible(origin.surface(), cell.surface());
    }

    private static boolean compatible(OverlaySurface a, OverlaySurface b) {
        return a.blockState().equals(b.blockState())
                && Math.abs(a.exposure() - b.exposure()) < EXPOSURE_MERGE_TOLERANCE;
    }
}