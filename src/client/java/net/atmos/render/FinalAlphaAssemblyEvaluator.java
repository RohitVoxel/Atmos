package net.atmos.render;

/**
 * Final Alpha Assembly evaluator — Appendix ZB Blocker 9. Terminal math
 * stage producing the value later consumed as {@link RenderCluster#alpha()}.
 *
 * --- Scope ---
 *
 * Implements only the six-input multiplication and Beer-Lambert
 * exponential mapping literally defined by Appendix ZB Blocker 9. It
 * computes none of the six inputs itself — per Appendix P's Producer
 * Ownership Registry, each belongs to a separately-owned upstream
 * producer:
 *
 *     confidence        -> Confidence System
 *                           (net.atmos.composition.ClusterConfidenceEvaluator)
 *     sunReach          -> SunReach System
 *                           (net.atmos.sunreach.SunReachCombinationEvaluator)
 *     exposureScale     -> Exposure Model (net.atmos.exposure.ExposureModel)
 *     fadeWeight        -> Distance & Fade Evaluation (Appendix ZB
 *                           Blocker 4 — no producer exists in this
 *                           codebase yet)
 *     compositionWeight -> Composition Engine (Chapter 10 Part 2's
 *                           Hero=1.00 / Secondary=0.70 / Ambient=0.40
 *                           hierarchy)
 *     lodWeight         -> LOD Assignment (Appendix ZB Blocker 6 — no
 *                           producer exists in this codebase yet)
 *
 * Blocker 4 and Blocker 6 have no producer anywhere in this codebase.
 * Per the no-fabrication rule, this evaluator does not invent,
 * approximate, or internally derive either value — it declares its
 * contract requires them as caller-supplied scalars, matching the
 * established sunreach-package idiom already documented on
 * {@code HumidityInteractionEvaluator} ("evaluators in this package
 * take primitives already extracted by the caller rather than depending
 * on the owning state class directly"). A future RenderCluster
 * Construction stage (Appendix Z — explicitly out of scope here) is
 * responsible for sourcing real values for every input and for
 * terminating construction of a given cluster entirely (per Appendix L
 * §8 / Appendix Y's failure handling — "no partial RenderClusters")
 * whenever a required upstream producer is unavailable. This evaluator
 * performs no such branch itself, because it never reaches into any
 * producer — it is a pure function of its six parameters only.
 *
 * compositionWeight is accepted as a pre-computed scalar rather than
 * re-derived from a {@link RenderCluster.Role}. Chapter 10 Part 2's
 * Hero=1.00/Secondary=0.70/Ambient=0.40 hierarchy is already read
 * independently by {@link DensityProbabilityMap} (Chapter 9 Stage 2,
 * frozen) for an unrelated purpose. Re-deriving that mapping here would
 * duplicate an existing calculation and touch frozen code; accepting it
 * as an input avoids both.
 *
 * --- Formula (Appendix ZB Blocker 9 §4) ---
 *
 *     A_raw   = confidence * sunReach * exposureScale
 *               * fadeWeight * compositionWeight * lodWeight
 *     A_final = 1 - exp(-A_raw * ALPHA_SCATTERING_COEFFICIENT)
 *
 * ALPHA_SCATTERING_COEFFICIENT = 1.2, reused verbatim from Appendix ZB
 * §III (RenderingMathConstants). The full RenderingMathConstants holder
 * (which also centralizes Blocker 1-8 constants) is intentionally not
 * created here, since none of those blockers are in scope for this
 * task; this evaluator instead owns only the one constant it needs,
 * matching the established per-evaluator constant-ownership convention
 * (e.g. {@code WeatherAttenuationEvaluator}'s own local constants).
 *
 * --- No input validation (Appendix ZB Blocker 9 §5) ---
 *
 * "Because all input scalars are strictly positive and properly clamped
 * upstream, A_raw is mathematically guaranteed to be >= 0.0... no
 * clamping beyond mathematically impossible floating-point safety." No
 * re-clamping or re-validation is performed here, mirroring
 * {@code SunReachCombinationEvaluator}'s identical "no hidden logic"
 * precedent for the nearest analogous Final-Combination producer.
 * A_final is self-bounded within [0,1) for any finite non-negative
 * A_raw by the exponential mapping itself.
 *
 * --- Threading, determinism, performance ---
 *
 * Stateless, side-effect-free, O(1): five multiplications, one
 * exponential, one record allocation. No caching, no world access, no
 * mutable static state. Deterministic. Simulation Thread only.
 *
 * --- Task boundary ---
 *
 * Final Alpha Assembly math only. Does not publish, construct, or
 * validate a {@link RenderCluster}; performs no RenderCluster
 * Construction (Appendix Z); does not integrate with ALSSRenderer,
 * AtmosClient, or any Fabric render event.
 */
public final class FinalAlphaAssemblyEvaluator {

    private FinalAlphaAssemblyEvaluator() {}

    /** Appendix ZB §III — RenderingMathConstants.ALPHA_SCATTERING_COEFFICIENT. */
    private static final float ALPHA_SCATTERING_COEFFICIENT = 1.2f;

    public static FinalAlphaResult evaluate(
            float confidence,
            float sunReach,
            float exposureScale,
            float fadeWeight,
            float compositionWeight,
            float lodWeight
    ) {
        float rawAlpha = confidence * sunReach * exposureScale
                * fadeWeight * compositionWeight * lodWeight;

        float finalAlpha = 1f - (float) Math.exp(-rawAlpha * ALPHA_SCATTERING_COEFFICIENT);

        return new FinalAlphaResult(rawAlpha, finalAlpha);
    }
}