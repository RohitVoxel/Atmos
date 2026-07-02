package net.atmos.confidence;

/**
 * Shared mathematical primitives for the Confidence System (Chapter 4).
 *
 * Architecturally distinct from FogMath: FogMath serves the fog rendering
 * pipeline specifically. ConfidenceMath serves the Confidence System layer,
 * which sits above EnvironmentalState and below the Cell Grid / Composition
 * Engine per the Chapter 2 pipeline. Per Chapter 2 §6 ("Atmospheric
 * simulation ≠ Rendering ≠ Configuration ≠ State management ≠
 * Interpolation"), these utilities are kept separate even though some
 * operations look similar, because they belong to different architectural
 * layers and will diverge as the Confidence System grows its own
 * tier-specific math (temporal blending, hero scoring, etc. — Chapter 4
 * §7-9, §15).
 */
public final class ConfidenceMath {

    private ConfidenceMath() {}

    // Floating-point tolerance for "weights sum to 1.0" validation.
    // Loose enough to absorb float accumulation error across a realistic
    // number of factors (up to ~10) without masking genuine misconfiguration.
    private static final float WEIGHT_SUM_TOLERANCE = 1e-3f;

    /**
     * Within-tier combination formula per Appendix D §7:
     *
     *   Tier Value = Product of (factor_i ^ weight_i) for all factors i
     *
     * where all weights sum to 1.0.
     *
     * This is a weighted geometric mean, not a weighted arithmetic mean.
     * The geometric form preserves the multiplicative philosophy mandated by
     * Chapter 4 §3 ("Why Multiplication?") at the individual-factor level: a
     * single factor at exactly 0 drives the whole tier to 0 regardless of
     * how high the other factors are (0^w = 0 for any w > 0), while still
     * allowing per-factor importance tuning via the exponent — something a
     * plain equal-weighted product cannot express.
     *
     * @param factors individual factor values, each expected in [0,1].
     *                Not clamped here — callers must supply normalized
     *                values, since silent clamping would hide upstream
     *                normalization bugs rather than surface them.
     * @param weights weights, same length as factors, each non-negative,
     *                summing to 1.0 (within WEIGHT_SUM_TOLERANCE).
     * @return the combined tier value in [0,1], assuming valid inputs.
     * @throws IllegalArgumentException if array lengths differ, if any
     *         weight is negative, if weights don't sum to ~1.0, or if any
     *         factor is negative (a negative base raised to a fractional
     *         exponent is undefined in the real domain and would otherwise
     *         silently produce NaN).
     */
    public static float weightedGeometricProduct(float[] factors, float[] weights) {
        if (factors.length != weights.length) {
            throw new IllegalArgumentException(
                    "factors and weights must be the same length: "
                            + factors.length + " vs " + weights.length);
        }

        float weightSum = 0f;
        for (float w : weights) {
            weightSum += requireNonNegativeWeight(w);
        }
        requireWeightSumIsOne(weightSum);

        float result = 1.0f;
        for (int i = 0; i < factors.length; i++) {
            result *= singleTerm(factors[i], weights[i], i);
        }

        return result;
    }

    /**
     * Allocation-free two-factor overload of {@link #weightedGeometricProduct(float[], float[])}.
     *
     * Added during the Confidence System Final Cleanup pass. Tier A and
     * Tier B each evaluate exactly two factors; since the Confidence System
     * may eventually evaluate hundreds of cells per frame once the Cluster
     * Builder exists, the two small array allocations per call (one for
     * factors, one for weights) that the array-based overload requires
     * become needless per-frame garbage at that scale. This overload
     * produces mathematically identical results — see the shared
     * {@link #singleTerm(float, float, int)} helper — without allocating
     * anything.
     *
     * Validation semantics are identical to the array-based overload:
     * negative weights and factors throw, and weights must sum to 1.0
     * within {@link #WEIGHT_SUM_TOLERANCE}.
     */
    public static float weightedGeometricProduct(float factor1, float weight1,
                                                 float factor2, float weight2) {
        float weightSum = requireNonNegativeWeight(weight1)
                + requireNonNegativeWeight(weight2);
        requireWeightSumIsOne(weightSum);

        return singleTerm(factor1, weight1, 0)
                * singleTerm(factor2, weight2, 1);
    }

    /**
     * Allocation-free three-factor overload of {@link #weightedGeometricProduct(float[], float[])}.
     *
     * Same rationale as the two-factor overload above. Added specifically
     * for Tier C, which evaluates exactly three factors (distance,
     * alignment, frustum).
     */
    public static float weightedGeometricProduct(float factor1, float weight1,
                                                 float factor2, float weight2,
                                                 float factor3, float weight3) {
        float weightSum = requireNonNegativeWeight(weight1)
                + requireNonNegativeWeight(weight2)
                + requireNonNegativeWeight(weight3);
        requireWeightSumIsOne(weightSum);

        return singleTerm(factor1, weight1, 0)
                * singleTerm(factor2, weight2, 1)
                * singleTerm(factor3, weight3, 2);
    }

    // -------------------------------------------------------------------
    // Shared validation and evaluation helpers.
    //
    // Extracted so the array-based overload and the two new allocation-free
    // overloads all execute the exact same math and throw the exact same
    // exceptions for the exact same invalid inputs — no duplicated logic
    // between them, and no behavioral drift possible between overloads.
    // -------------------------------------------------------------------

    private static float requireNonNegativeWeight(float w) {
        if (w < 0f) {
            throw new IllegalArgumentException("weight cannot be negative: " + w);
        }
        return w;
    }

    private static void requireWeightSumIsOne(float weightSum) {
        if (Math.abs(weightSum - 1.0f) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException("weights must sum to 1.0, got " + weightSum);
        }
    }

    /**
     * Evaluates one factor^weight term, with the same edge-case handling
     * the original single-method implementation had:
     *
     *   factor=0, weight>0  -> 0^weight = 0, correctly zeroes the tier.
     *   factor=0, weight=0  -> Math.pow returns 1.0 by convention
     *                          (0^0 = 1); a zero-weighted factor is
     *                          inert by definition, which is correct.
     */
    private static float singleTerm(float factor, float weight, int index) {
        if (factor < 0f) {
            throw new IllegalArgumentException(
                    "factor[" + index + "] is negative (" + factor
                            + ") — negative bases are undefined for fractional exponents");
        }
        return (float) Math.pow(factor, weight);
    }
}