package net.atmos.render;

/**
 * Stateless, deterministic weight evaluator implementing the Density
 * Probability Map described by Appendix H.
 *
 * Per Appendix H §3, the Density Probability Map is not a stored grid or
 * cache — it is a continuous weighting FUNCTION evaluated per candidate
 * shaft sample. This class holds no fields and no per-cluster state;
 * every call is a pure function of its arguments.
 *
 * --- Scope of this stage (Chapter 9 Stage 2) ---
 *
 * Appendix H §5 defines seven weighting components: Occlusion, Atmospheric
 * Density, View Alignment, Distance, Hero Priority, Procedural Noise, and
 * Performance. Of these, four require inputs outside this stage's declared
 * consumption surface (RenderCluster only) — Occlusion and View Alignment
 * need Cell Grid/CameraSnapshot data, Distance needs CameraSnapshot, and
 * Performance needs an APS/ALSC OptimizationPlan. None of those are
 * implemented here.
 *
 * --- M1 correction (Chapter 9 Stage 2 Final Cleanup) ---
 *
 * A prior revision of this class approximated Atmospheric Density Weight
 * using RenderCluster.alpha(), on the stated basis that RenderCluster
 * carries no separate raw humidity/fog field. That approximation was
 * architecturally incorrect: Appendix L defines RenderCluster.alpha() as
 * the cluster's single, already-finalized composed alpha, and
 * ShaftDescriptor's own contract (see RendererExpansion) already
 * multiplies cluster.alpha() into the final per-shaft brightness
 * downstream. Reusing alpha here as well caused alpha to be applied
 * twice — effectively squaring it — which violates Appendix L's "single
 * final multiplier" contract and Appendix H §9's "no hidden logic"
 * requirement.
 *
 * Per the "no placeholder logic" rule, the correct resolution is
 * omission, not substitution: this stage does NOT implement Atmospheric
 * Density Weight. RenderCluster has no independent density signal
 * distinct from alpha, so no non-duplicating approximation exists yet.
 * That component remains undone until a future stage introduces a
 * genuine, separately-owned density field on the render contract.
 *
 * This stage now implements exactly the two components derivable from a
 * RenderCluster alone without colliding with a downstream application of
 * the same data:
 *
 *   - Hero Priority Weight — derived directly from RenderCluster.role(),
 *     reusing the exact brightness hierarchy ratios already documented in
 *     Chapter 10 Part 2 ("Brightness Hierarchy": Hero 100%, Secondary 70%,
 *     Ambient 40%) rather than inventing new ratios.
 *   - Procedural Noise — low-amplitude, deterministic, seed-derived, per
 *     Appendix H §5.6 ("Noise shall only introduce visual variation.
 *     Noise shall never determine atmospheric behavior.").
 *
 * Alpha itself is applied exactly once, downstream, in
 * RendererExpansion.buildShaft() — per ShaftDescriptor's own documented
 * contract. This class must never reintroduce cluster.alpha() into its
 * weight formula.
 *
 * The remaining two weights are combined multiplicatively, matching
 * Appendix H §6's conceptual formula and the project's existing
 * multiplicative philosophy for combining independent attenuation-like
 * factors (Appendix D §7, Appendix K §K.7).
 */
public final class DensityProbabilityMap {

    private DensityProbabilityMap() {}

    // Chapter 10 Part 2 "Brightness Hierarchy" — reused verbatim, not
    // reinvented.
    private static final float HERO_PRIORITY_WEIGHT      = 1.00f;
    private static final float SECONDARY_PRIORITY_WEIGHT = 0.70f;
    private static final float AMBIENT_PRIORITY_WEIGHT   = 0.40f;

    // Small by design — Appendix H §5.6 requires noise to only introduce
    // visual variation, never determine atmospheric behavior.
    private static final float NOISE_AMPLITUDE = 0.12f;

    /**
     * Evaluates the continuous, deterministic density weight for one
     * candidate shaft sample belonging to {@code cluster}.
     *
     * Intentionally does NOT read {@code cluster.alpha()} — see class doc
     * (M1 correction). Alpha is applied exactly once, downstream, by
     * RendererExpansion.
     *
     * @param cluster    the immutable parent RenderCluster.
     * @param sampleSeed a deterministic per-sample seed (already mixed by
     *                   SeedHash.deriveSeed), used only for the noise term.
     */
    public static float evaluate(RenderCluster cluster, long sampleSeed) {
        float heroPriority = heroPriorityWeight(cluster.role());
        float noise        = proceduralNoise(sampleSeed);

        return heroPriority * noise;
    }

    private static float heroPriorityWeight(RenderCluster.Role role) {
        return switch (role) {
            case HERO      -> HERO_PRIORITY_WEIGHT;
            case SECONDARY -> SECONDARY_PRIORITY_WEIGHT;
            case AMBIENT   -> AMBIENT_PRIORITY_WEIGHT;
        };
    }

    /**
     * Deterministic low-amplitude noise in
     * [1 - NOISE_AMPLITUDE, 1 + NOISE_AMPLITUDE]. Identical seeds always
     * produce identical noise — required by Appendix G §6.
     */
    private static float proceduralNoise(long sampleSeed) {
        float unit = SeedHash.toUnitFloat(sampleSeed);
        return 1f + (unit * 2f - 1f) * NOISE_AMPLITUDE;
    }
}