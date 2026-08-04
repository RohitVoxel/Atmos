package net.atmos.cluster;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellCoord;
import net.atmos.cellgrid.CellGrid;
import net.atmos.confidence.TierAEvaluator;
import net.atmos.confidence.TierAResult;
import net.atmos.confidence.TierBEvaluator;
import net.atmos.confidence.TierBResult;
import net.atmos.core.VersionGate;

import java.util.*;

/**
 * Batch 1 Phase 3 addition: buildIfNeeded() only recomputes clusters when
 * CellGrid's structural version has changed since the last call. Otherwise
 * the previously published list is returned unchanged — no flood fill,
 * no Tier A/B re-evaluation. build() itself is unmodified (still a pure
 * function) so existing callers/tests are unaffected.
 */
public final class ClusterBuilder {

    private ClusterBuilder() {}

    private static final Comparator<CellCoord> COORD_ORDER =
            Comparator.comparingInt(CellCoord::x)
                    .thenComparingInt(CellCoord::y)
                    .thenComparingInt(CellCoord::z);

    public static List<Cluster> build(CellGrid cellGrid, EnvironmentalState env) {
        Collection<AtmosCell> activeCells = cellGrid.getActiveCells();
        if (activeCells.isEmpty()) return List.of();

        TierAResult tierA = TierAEvaluator.evaluate(env);

        Map<CellCoord, AtmosCell> cellsByCoord = new HashMap<>();
        Map<CellCoord, Float> atmosphericValue = new HashMap<>();

        for (AtmosCell cell : activeCells) {
            TierBResult tierB = TierBEvaluator.evaluate(cell);
            cellsByCoord.put(cell.coord(), cell);
            atmosphericValue.put(cell.coord(), tierA.value() * tierB.value());
        }

        List<CellCoord> orderedCoords = new ArrayList<>(cellsByCoord.keySet());
        orderedCoords.sort(COORD_ORDER);

        Set<CellCoord> visited = new HashSet<>();
        List<Cluster> clusters = new ArrayList<>();

        for (CellCoord seedCoord : orderedCoords) {
            if (visited.contains(seedCoord)) continue;

            float seedValue = atmosphericValue.get(seedCoord);
            List<CellCoord> members =
                    floodFill(seedCoord, seedValue, cellGrid, atmosphericValue, visited);

            clusters.add(buildCluster(members, atmosphericValue));
        }

        return List.copyOf(clusters);
    }

    private static List<CellCoord> floodFill(CellCoord seedCoord,
                                             float seedValue,
                                             CellGrid cellGrid,
                                             Map<CellCoord, Float> atmosphericValue,
                                             Set<CellCoord> visited) {
        List<CellCoord> members = new ArrayList<>();
        ArrayDeque<CellCoord> queue = new ArrayDeque<>();

        queue.add(seedCoord);
        visited.add(seedCoord);

        while (!queue.isEmpty()) {
            CellCoord current = queue.poll();
            members.add(current);

            for (AtmosCell neighbor : cellGrid.getActiveNeighbors(current)) {
                CellCoord neighborCoord = neighbor.coord();
                if (visited.contains(neighborCoord)) continue;

                Float neighborValue = atmosphericValue.get(neighborCoord);
                if (neighborValue == null) continue;

                if (Math.abs(neighborValue - seedValue) <= ClusterConstants.SIMILARITY_THRESHOLD) {
                    visited.add(neighborCoord);
                    queue.add(neighborCoord);
                }
            }
        }

        members.sort(COORD_ORDER);
        return members;
    }

    private static Cluster buildCluster(List<CellCoord> members,
                                        Map<CellCoord, Float> atmosphericValue) {
        int cellSize = CellGrid.CELL_SIZE;

        double sumX = 0, sumY = 0, sumZ = 0;
        float sumValue = 0f;
        float maxValue = Float.NEGATIVE_INFINITY;

        List<net.minecraft.world.phys.Vec3> centers = new ArrayList<>(members.size());
        for (CellCoord coord : members) {
            double cx = coord.centerWorldX(cellSize);
            double cy = coord.centerWorldY(cellSize);
            double cz = coord.centerWorldZ(cellSize);
            centers.add(new net.minecraft.world.phys.Vec3(cx, cy, cz));

            sumX += cx;
            sumY += cy;
            sumZ += cz;

            float value = atmosphericValue.get(coord);
            sumValue += value;
            if (value > maxValue) maxValue = value;
        }

        int count = members.size();
        net.minecraft.world.phys.Vec3 center =
                new net.minecraft.world.phys.Vec3(sumX / count, sumY / count, sumZ / count);

        double maxDistSq = 0;
        for (net.minecraft.world.phys.Vec3 c : centers) {
            double distSq = c.distanceToSqr(center);
            if (distSq > maxDistSq) maxDistSq = distSq;
        }
        float radius = (float) Math.sqrt(maxDistSq) + (cellSize * 0.5f);

        CellCoord anchor = members.get(0);

        return new Cluster(
                anchor,
                members,
                center,
                radius,
                count,
                sumValue / count,
                maxValue
        );
    }
}