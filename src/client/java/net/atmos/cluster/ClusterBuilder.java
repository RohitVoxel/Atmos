package net.atmos.cluster;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellCoord;
import net.atmos.cellgrid.CellGrid;
import net.atmos.confidence.TierAEvaluator;
import net.atmos.confidence.TierAResult;
import net.atmos.confidence.TierBEvaluator;
import net.atmos.confidence.TierBResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Cluster Builder (Chapter 7).
 *
 * Responsibility: convert the set of currently active Atmos Cells into a
 * small number of spatially coherent, atmospherically similar Clusters.
 *
 * What this class owns:
 *   - Cluster membership determination (which cells belong together).
 *   - Cluster construction (geometric/statistical summarization).
 *
 * What this class deliberately does NOT own:
 *   - Environmental simulation            → EnvironmentalState (Ch. 3)
 *   - Camera / presentation logic         → Confidence Tier C, future
 *                                            Composition Engine (Ch. 10)
 *   - Rendering                           → ALSS Renderer (Ch. 9)
 *   - Beauty / hero evaluation            → Composition Engine (Ch. 10)
 *   - Performance scaling                 → APS / ALSC (Ch. 16)
 *   - Biome transition blending           → FogInterpolator (existing)
 *
 * Camera independence (important architectural note):
 * Chapter 4 defines Confidence as Tier A × Tier B × Tier C, but Tier C is
 * explicitly camera-relative (Appendix B §3 — distance/alignment/frustum
 * from CameraSnapshot). Cluster Builder must never own camera logic
 * (explicit rule in this chapter's spec), so clustering similarity is
 * computed from Tier A × Tier B only — the camera-independent atmospheric
 * possibility and local-opportunity signal. This is "existing Confidence
 * information," reused directly via TierAEvaluator/TierBEvaluator, not a
 * second evaluation system. Tier C is intentionally never touched here.
 *
 * Tuning values: all clustering thresholds live exclusively in
 * ClusterConstants, mirroring the ConfidenceWeights pattern used by the
 * Confidence System. This class declares no tuning constants of its own.
 *
 * Determinism (Chapter 7 §"Architectural Rules"):
 * Given the same active cells and the same EnvironmentalState, this class
 * always produces identical clusters:
 *   - Tier A is evaluated once from EnvironmentalState and reused for
 *     every cell in the pass — no cell-to-cell variance from re-sampling.
 *   - Tier B is evaluated per cell from already-deterministic AtmosCell /
 *     HorizonMap data.
 *   - Cell traversal order is normalized (sorted by CellCoord) before
 *     processing so that HashMap iteration order (CellGrid's internal
 *     storage) can never influence which cluster "claims" a given cell.
 *   - Cluster membership is decided by comparing each candidate cell's
 *     atmospheric value against its cluster's fixed seed value (see
 *     below), never against a chain of neighbors — this makes the
 *     resulting connected component a pure function of graph structure
 *     and per-cell values, independent of BFS visitation order.
 *   - No randomness, no wall-clock/tick-based branching.
 *
 * Membership rule:
 * A candidate cell joins a cluster if it is reachable from the cluster's
 * seed cell via active-cell face-adjacency (CellGrid.getActiveNeighbors),
 * passing only through cells whose Tier A × Tier B value differs from the
 * seed's value by no more than ClusterConstants.SIMILARITY_THRESHOLD.
 * Comparing against a fixed seed value (rather than the immediately
 * preceding cell) avoids unbounded "value drift" across a long chain of
 * barely-similar cells, keeping the rule simple, explainable, and free of
 * magic behavior.
 */
public final class ClusterBuilder {

    private ClusterBuilder() {}

    private static final Comparator<CellCoord> COORD_ORDER =
            Comparator.comparingInt(CellCoord::x)
                    .thenComparingInt(CellCoord::y)
                    .thenComparingInt(CellCoord::z);

    /**
     * Builds the full set of Clusters from the Cell Grid's currently active
     * cells, using EnvironmentalState for the (camera-independent) Tier A
     * atmospheric-possibility signal.
     *
     * @return immutable list of Clusters, in deterministic anchor-coordinate
     *         order. Empty list if there are no active cells.
     */
    public static List<Cluster> build(CellGrid cellGrid, EnvironmentalState env) {
        Collection<AtmosCell> activeCells = cellGrid.getActiveCells();
        if (activeCells.isEmpty()) return List.of();

        // Tier A depends only on EnvironmentalState — evaluate once, reuse
        // for every cell this pass. This is what makes clustering camera-
        // and-cell-independent for the "possibility" half of the signal.
        TierAResult tierA = TierAEvaluator.evaluate(env);

        Map<CellCoord, AtmosCell> cellsByCoord = new HashMap<>();
        Map<CellCoord, Float> atmosphericValue = new HashMap<>();

        for (AtmosCell cell : activeCells) {
            TierBResult tierB = TierBEvaluator.evaluate(cell);
            cellsByCoord.put(cell.coord(), cell);
            atmosphericValue.put(cell.coord(), tierA.value() * tierB.value());
        }

        // Normalize traversal order — decouples the result from CellGrid's
        // internal HashMap iteration order (Chapter 7 determinism rule).
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

    /**
     * Discovers every active cell reachable from {@code seedCoord} via
     * face-adjacency, passing only through cells whose atmospheric value is
     * within ClusterConstants.SIMILARITY_THRESHOLD of {@code seedValue}.
     * Standard BFS over a bounded local region (CellGrid's active radius is
     * small — see CellGrid.HORIZONTAL_RADIUS/VERTICAL_RADIUS), so this
     * terminates quickly and touches each cell at most once thanks to
     * {@code visited}.
     */
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
                if (neighborValue == null) continue; // not part of this pass's cell set

                if (Math.abs(neighborValue - seedValue) <= ClusterConstants.SIMILARITY_THRESHOLD) {
                    visited.add(neighborCoord);
                    queue.add(neighborCoord);
                }
            }
        }

        members.sort(COORD_ORDER);
        return members;
    }

    /**
     * Summarizes a discovered member set into an immutable Cluster.
     * Purely arithmetic — no decisions, no thresholds, no branching beyond
     * the single-cell radius special case.
     */
    private static Cluster buildCluster(List<CellCoord> members,
                                        Map<CellCoord, Float> atmosphericValue) {
        int cellSize = CellGrid.CELL_SIZE;

        double sumX = 0, sumY = 0, sumZ = 0;
        float sumValue = 0f;
        float maxValue = Float.NEGATIVE_INFINITY;

        List<Vec3> centers = new ArrayList<>(members.size());
        for (CellCoord coord : members) {
            double cx = coord.centerWorldX(cellSize);
            double cy = coord.centerWorldY(cellSize);
            double cz = coord.centerWorldZ(cellSize);
            centers.add(new Vec3(cx, cy, cz));

            sumX += cx;
            sumY += cy;
            sumZ += cz;

            float value = atmosphericValue.get(coord);
            sumValue += value;
            if (value > maxValue) maxValue = value;
        }

        int count = members.size();
        Vec3 center = new Vec3(sumX / count, sumY / count, sumZ / count);

        // Radius: farthest member cell center from the cluster center,
        // plus half a cell width so the cluster volume actually encloses
        // its member cells rather than just their center points.
        double maxDistSq = 0;
        for (Vec3 c : centers) {
            double distSq = c.distanceToSqr(center);
            if (distSq > maxDistSq) maxDistSq = distSq;
        }
        float radius = (float) Math.sqrt(maxDistSq) + (cellSize * 0.5f);

        CellCoord anchor = members.get(0); // already sorted — smallest coord

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