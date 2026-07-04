package net.atmos.cellgrid;

/**
 * Immutable, deterministic record of raw per-slab foliage presence samples
 * affecting one Atmospheric Cell, per Chapter 8 §13 (Stage Four: Canopy
 * Occlusion).
 *
 * Cell Grid ownership philosophy: HorizonMap stores raw, unconverted
 * per-sector elevation angles — conversion happens downstream in
 * SunReachEvaluator/TierBEvaluator, never inside HorizonMap. This class
 * follows the identical philosophy: raw per-slab boolean hits, uninterpreted.
 * All physical combination (proximity weighting, saturation curve) belongs
 * to CanopyOcclusionEvaluator (SunReach), never here.
 *
 * SEARCH_HEIGHT_BLOCKS is a fixed, self-contained SAMPLING HORIZON — the
 * maximum vertical distance within which overhead material is considered
 * at all — not a claim about real vegetation height. It is chosen jointly
 * with CanopyOcclusionEvaluator.PROXIMITY_SCALE (5.0f) to maintain a ratio
 * of exactly 5.0: five e-folding lengths of that evaluator's decay model,
 * so the farthest possible sample already carries weight e^-5 ≈ 0.0067 —
 * architecturally negligible by construction of the decay model, not by
 * assumption about tree height. This class is the canonical location for
 * that relationship; CanopyOcclusionEvaluator cross-references it rather
 * than re-deriving it. If either constant is retuned, re-check this ratio.
 * No code coupling exists between the two constants — only documented
 * design intent, mirroring HorizonMap.SECTOR_COUNT's relationship to
 * SunReachEvaluator.
 *
 * Sample placement uses a quadratic bias (fraction^2) rather than linear
 * spacing, concentrating samples near the reference point where proximity
 * weighting says they matter most. Quadratic was chosen over an
 * exponential-spaced schedule (which would mirror the weighting curve more
 * closely) because it is simpler, needs no renormalization to hit the
 * window's exact endpoints, and the marginal detection benefit of matching
 * curves exactly does not justify the added complexity. This does not, and
 * cannot, eliminate sampling aliasing entirely — any finite deterministic
 * sampling of a continuous domain can be defeated by an adversarial pattern
 * at higher spatial frequency than the sample spacing (the same limit
 * described by the Nyquist sampling theorem). It only reallocates the fixed
 * sample budget to where the physical model says it matters most.
 *
 * sampleFraction() is exposed here — not duplicated in the generator and
 * evaluator separately — so both consumers share one authoritative
 * placement schedule. This mirrors HorizonMap.SECTOR_COUNT being read
 * across the cellgrid/sunreach/confidence package boundary: a pure,
 * stateless piece of shared math, not a world-sampling responsibility.
 *
 * Binary per-slab classification is not a simplification Atmos is
 * choosing — vanilla LeavesBlock carries no fractional density field in
 * its BlockState; a block is either foliage or it is not.
 */
public final class CanopyProfile {

    /** Number of horizontal probe columns sampled around the cell's center. Implementation tuning. */
    public static final int SAMPLE_COUNT = 5;

    /** Number of vertical probes sampled per column. Implementation tuning, bounded for O(1) cost. */
    public static final int VERTICAL_SAMPLE_COUNT = 8;

    /**
     * Fixed vertical sampling horizon, in blocks, above each column's
     * reference Y. Architectural invariant — see class doc for the
     * canonical explanation of its relationship to
     * CanopyOcclusionEvaluator.PROXIMITY_SCALE.
     */
    public static final float SEARCH_HEIGHT_BLOCKS = 25.0f;

    // foliageHits[column][verticalSampleIndex]. Index 0 = nearest to the
    // reference Y; index VERTICAL_SAMPLE_COUNT-1 = farthest. Distance for
    // any index is reconstructed via sampleFraction() — never stored
    // redundantly.
    private final boolean[][] foliageHits;

    public CanopyProfile(boolean[][] foliageHits) {
        if (foliageHits.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException(
                    "CanopyProfile requires exactly " + SAMPLE_COUNT + " columns, got "
                            + foliageHits.length);
        }
        boolean[][] copy = new boolean[SAMPLE_COUNT][];
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            if (foliageHits[i].length != VERTICAL_SAMPLE_COUNT) {
                throw new IllegalArgumentException(
                        "CanopyProfile column " + i + " requires exactly "
                                + VERTICAL_SAMPLE_COUNT + " vertical samples, got "
                                + foliageHits[i].length);
            }
            // Defensive deep copy — matches HorizonMap's double-copy discipline
            // (constructor + every read).
            copy[i] = foliageHits[i].clone();
        }
        this.foliageHits = copy;
    }

    /**
     * Read-only deep copy of every column's raw per-slab foliage hits.
     * Row = column index [0, SAMPLE_COUNT); column = vertical sample index
     * [0, VERTICAL_SAMPLE_COUNT), nearest-to-farthest.
     */
    public boolean[][] hitsView() {
        boolean[][] copy = new boolean[foliageHits.length][];
        for (int i = 0; i < foliageHits.length; i++) {
            copy[i] = foliageHits[i].clone();
        }
        return copy;
    }

    /**
     * Returns the biased placement fraction in [0,1] for a given vertical
     * sample index, using the quadratic near-field bias described above.
     * The single authoritative schedule shared by CanopyProfileGenerator
     * (choosing sample Y positions) and CanopyOcclusionEvaluator
     * (reconstructing each sample's distance for proximity weighting).
     */
    public static float sampleFraction(int sampleIndex) {
        if (VERTICAL_SAMPLE_COUNT <= 1) return 0f;
        float linearFraction = (float) sampleIndex / (VERTICAL_SAMPLE_COUNT - 1);
        return linearFraction * linearFraction;
    }
}