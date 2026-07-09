package net.atmos.composition;

/**
 * Centralized tuning constants for Chapter 10 Stage 2 (Composition
 * Selection), mirroring the {@code ConfidenceWeights} / {@code
 * ClusterConstants} pattern — no Composition evaluator class may declare
 * its own tuning constant.
 *
 * --- Scope reduction from Chapter 10 Part 3 ---
 *
 * Chapter 10 Part 3 defines Hero Score, Secondary Score, and Ambient Score
 * as products of several named sub-factors. Several have no data source
 * available to Stage 2 and are intentionally omitted rather than
 * approximated — an omitted factor contributes no term to the product
 * (equivalent to being absent, not to being fixed at 1.0):
 *
 *   Hero Score      — Travel Alignment (no travel-direction data anywhere
 *                     in the codebase; explicitly excluded from this task),
 *                     Sun Reach (not threaded into the Composition pipeline
 *                     — see CompositionInputs class doc), and Temporal
 *                     Stability (Composition Memory, Chapter 10 Part 4;
 *                     explicitly excluded from this task) are omitted.
 *                     Confidence, Depth Quality, and Uniqueness remain.
 *
 *   Secondary role  — Angular Balance, Depth Difference, Moderate
 *                     Brightness, and Spacing Quality have no numeric
 *                     anchors anywhere in Chapter 10. Selection instead
 *                     uses Confidence-ranked ordering plus Hard Composition
 *                     Rule 4's explicitly anchored minimum angular
 *                     separation ("≈ 8°").
 *
 *   Ambient role    — Peripheral Position, Low Competition, and Visual
 *                     Balance have no data source. Selection uses
 *                     Confidence (via the viability threshold) only.
 *
 * Hard Composition Rule 2 (Secondary never brighter than Hero) and Rule 3
 * (Ambient avoids visual center) require rendered brightness / screen-space
 * position, neither of which exists before Chapter 9's RenderCluster
 * conversion — deferred, not enforced here.
 */
public final class CompositionWeights {

    private CompositionWeights() {}

    /**
     * Minimum per-cluster Confidence (Chapter 4 §4) for a candidate to be
     * eligible for any composition role. Below this, a candidate is
     * Rejected. No exact composition-level figure exists in the Master
     * Guide; implementation-defined, chosen conservatively.
     */
    public static final float MIN_VIABLE_CONFIDENCE = 0.35f;

    // Depth Quality bands, in blocks (Chapter 10 Part 3, "Depth Quality").
    public static final float DEPTH_NEAR_MIN  = 6f;
    public static final float DEPTH_IDEAL_MIN = 20f;
    public static final float DEPTH_IDEAL_MAX = 45f;
    public static final float DEPTH_FAR_MAX   = 80f;

    /** Additional distance beyond {@link #DEPTH_FAR_MAX} over which the Extreme penalty saturates. */
    public static final float DEPTH_EXTREME_RANGE = 80f;

    public static final float DEPTH_NEAR_FLOOR       = 0.25f;
    public static final float DEPTH_ACCEPTABLE_FLOOR = 0.55f;
    public static final float DEPTH_EXTREME_FLOOR    = 0.10f;

    /** Normalization range for the Uniqueness deviation term (atmospheric-value units, [0,1] domain). */
    public static final float UNIQUENESS_NORMALIZATION_RANGE = 0.30f;

    /** Soft floor preventing Uniqueness from hard-collapsing to zero for near-mean candidates. */
    public static final float UNIQUENESS_FLOOR = 0.20f;

    /** Hard Composition Rule 4 (Chapter 10 Part 3): "Minimum Separation ≈ 8°". */
    public static final float MIN_ANGULAR_SEPARATION_RADIANS = (float) Math.toRadians(8.0);

    /** "Typical count 2–4" (Chapter 10 Part 2); fixed at the upper bound pending future APS integration. */
    public static final int MAX_SECONDARY_COUNT = 4;
}