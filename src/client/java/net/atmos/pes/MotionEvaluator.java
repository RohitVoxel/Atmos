package net.atmos.pes;

import net.atmos.cellgrid.CellCoord;
import net.atmos.cellgrid.CellGrid;

/**
 * Motion Evaluation — Chapter 12 §12.26.
 *
 * Estimates spatial traversal rate from the trailing window of recorded
 * Hero anchors (PESHistoryEntry.heroAnchor(), already captured from
 * Composition per §12.30) — no new PES input is introduced. Distance is
 * measured between consecutive non-null anchors' world-space cell centers,
 * reusing CellGrid.CELL_SIZE rather than inventing a second coordinate
 * scale. Pairs spanning a null anchor (no Hero that entry) are skipped —
 * Hero absence is not evidence of movement either way.
 *
 * §12.26: "Elytra Flight -> Rapid Spatial Traversal -> Disable Spatial
 * Repetition Checks -> Shift Evaluation to Temporal Stability." This
 * evaluator only measures rapidTraversal; gating Pattern Repetition and
 * the Overall Score is orchestration-layer work — see
 * PerceptualEvaluationSystem (Stage 6).
 *
 * Threshold/window are implementation-defined — §12.26 gives no numeric
 * anchor — same status as every other PES tolerance in PESWeights.
 * meanAnchorDistancePerStep is blocks per history step, not blocks/second:
 * PESHistoryEntry.evaluationSequence is an opaque monotonic counter, not
 * wall-clock time (see that record's class doc), so no real speed unit is
 * derivable here.
 */
public final class MotionEvaluator {

    private MotionEvaluator() {}

    public static MotionResult evaluate(PESHistoryView history, PESHistoryEntry current) {
        int size = history.size();
        int windowStart = Math.max(0, size - (PESWeights.MOTION_WINDOW_SIZE - 1));

        double distanceSum = 0.0;
        int pairs = 0;

        CellCoord previous = (size > 0) ? history.get(windowStart).heroAnchor() : null;
        for (int i = windowStart + 1; i < size; i++) {
            CellCoord anchor = history.get(i).heroAnchor();
            if (previous != null && anchor != null) {
                distanceSum += anchorDistance(previous, anchor);
                pairs++;
            }
            previous = anchor;
        }

        CellCoord currentAnchor = current.heroAnchor();
        if (previous != null && currentAnchor != null) {
            distanceSum += anchorDistance(previous, currentAnchor);
            pairs++;
        }

        if (pairs == 0) {
            return new MotionResult(0f, false);
        }

        float meanDistancePerStep = (float) (distanceSum / pairs);
        boolean rapidTraversal = meanDistancePerStep >= PESWeights.MOTION_RAPID_TRAVERSAL_THRESHOLD;

        return new MotionResult(meanDistancePerStep, rapidTraversal);
    }

    private static double anchorDistance(CellCoord a, CellCoord b) {
        int cellSize = CellGrid.CELL_SIZE;
        double dx = a.centerWorldX(cellSize) - b.centerWorldX(cellSize);
        double dy = a.centerWorldY(cellSize) - b.centerWorldY(cellSize);
        double dz = a.centerWorldZ(cellSize) - b.centerWorldZ(cellSize);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}