package net.atmos.sunreach;

/**
 * Final SunReach Combination evaluator — Chapter 8 §17–§18, fully specified
 * by Appendix K ("Final SunReach Combination Contract").
 *
 * --- Scope (Appendix K §K.1) ---
 *
 * This class governs only the final computational combination of the
 * already-completed SunReach stages. It does not redefine, recompute, or
 * modify Solar Position, Terrain Exposure, Sky Visibility, Canopy
 * Occlusion, Weather Influence, Humidity Interaction, or Biome Modifiers —
 * each of those stages remains individually owned by its existing
 * evaluator ({@link SunReachEvaluator}, {@link SkyVisibilityEvaluator},
 * {@link CanopyOcclusionEvaluator}, {@link WeatherAttenuationEvaluator},
 * {@link BiomeModifierEvaluator}). This evaluator only multiplies their
 * already-produced outputs together.
 *
 * --- Ownership (Appendix K §K.2) ---
 *
 * Owned exclusively by {@code net.atmos.sunreach}. No other package
 * (FogManager, EnvironmentalState, Renderer, Cell Grid, Confidence System,
 * AtmosClient) may perform an independent SunReach multiplication — those
 * systems may only consume this evaluator's output once a future
 * integration task wires it in (explicitly out of scope here, per
 * Appendix K §K.15).
 *
 * --- Inputs (Appendix K §K.3) ---
 *
 * Consumes exactly the five stage outputs named below — no additional
 * factors are read or accepted:
 *
 *   Stage 1–2  ({@link SunReachResult#value()})
 *   Stage 3    ({@link SkyVisibilityResult#value()})
 *   Stage 4    ({@link CanopyOcclusionResult#value()})
 *   Stage 5    ({@link WeatherAttenuationResult#value()})
 *   Stage 7    ({@link BiomeModifierResult#biomeModifierFactor()})
 *
 * Per Appendix K §K.16 ("Extend Before Create"), each of these result
 * objects is reused verbatim as produced by its existing evaluator. None
 * of the five upstream stage evaluators or their algorithms are modified,
 * reimplemented, or duplicated by this class.
 *
 * --- Explicit exclusion of Stage Six (Appendix K §K.4) ---
 *
 * {@link HumidityInteractionResult} does NOT participate in this
 * combination. This is not an omission — it is a permanent architectural
 * decision. Humidity represents scattering efficiency, not sunlight
 * availability (Appendix I), and must never reduce or enhance SunReach
 * itself. HumidityInteractionResult remains an independent sibling signal
 * for a future rendering/composition stage outside Chapter 8 (Appendix K
 * §K.17). This method does not accept a HumidityInteractionResult
 * parameter at all, making the exclusion structurally enforced rather than
 * merely documented.
 *
 * --- Canonical formula (Appendix K §K.5) ---
 *
 *     Final SunReach
 *         = SunReachResult.value()
 *         × SkyVisibilityResult.value()
 *         × CanopyOcclusionResult.value()
 *         × WeatherAttenuationResult.value()
 *         × BiomeModifierResult.biomeModifierFactor()
 *
 * No additional factors are introduced. No stage is omitted. No stage
 * appears twice.
 *
 * --- Canonical order (Appendix K §K.6) ---
 *
 * Multiplication is mathematically commutative, but per Appendix K §K.6
 * every implementation must evaluate in the fixed canonical order above
 * (Solar/Terrain → Sky → Canopy → Weather → Biome) purely for
 * debugging/explainability consistency across the codebase. This
 * evaluator's expression follows that order exactly and must not be
 * reordered.
 *
 * --- Why multiplication (Appendix K §K.7) ---
 *
 * Each stage represents one independent limitation on available sunlight.
 * Multiplication correctly models independent, proportional attenuation:
 * good terrain exposure combined with a heavy storm must never produce
 * near-full sunlight, which an additive combination would incorrectly
 * allow. This mirrors the identical rationale already established for
 * SunReachEvaluator's own internal Stage One × Stage Two combination.
 *
 * --- Output range (Appendix K §K.8) ---
 *
 * Expected result range is [0.0, 1.10]. The 1.10 ceiling arises solely
 * from BiomeModifierResult's maximum enhancement factor (Appendix J §6).
 * No normalization, clamping, weighting, interpolation, smoothing, or bias
 * is applied here beyond the raw product itself, per Appendix K §K.9 ("no
 * hidden logic... no clamping beyond mathematically impossible
 * floating-point safety") — none of the five inputs can combine to exceed
 * this range or go negative given their own documented bounds, so no
 * floating-point safety clamp is even needed in practice.
 *
 * --- Explainability (Appendix K §K.10) ---
 *
 * Every contributing stage factor is preserved in the returned
 * {@link SunReachCombinationResult} exactly as received — nothing is
 * discarded, and no stage requires recomputation to inspect its individual
 * contribution to the final value.
 *
 * --- Integration boundary (Appendix K §K.15) ---
 *
 * This class authorizes only the mathematical combination described above.
 * It does not perform, and this task does not include:
 *   - renderer integration
 *   - shaft rendering
 *   - Cell Grid updates
 *   - Confidence System updates
 *   - AtmosClient changes
 *   - FogManager changes
 * Those remain explicitly for later chapters/tasks.
 *
 * --- Threading, determinism, performance (Appendix K §K.12–K.14) ---
 *
 * Stateless, side-effect-free, O(1): four floating-point multiplications
 * and one record allocation. No mutable static fields, no caching, no
 * world access, no renderer access. Given identical stage results, this
 * evaluator always produces an identical combined value — determinism
 * follows directly from the determinism already guaranteed by each
 * upstream evaluator. Safe for Simulation Thread use, matching every other
 * evaluator in this package.
 *
 * --- Task boundary ---
 *
 * Final SunReach Combination only, per Chapter 8 §17–§18 and Appendix K in
 * full. Not wired into AtmosClient, FogManager, the Confidence System,
 * Cell Grid, or any renderer — matching the same "inert, standalone"
 * pattern already established by every other sunreach evaluator awaiting
 * its future integration task.
 */
public final class SunReachCombinationEvaluator {

    private SunReachCombinationEvaluator() {}

    public static SunReachCombinationResult evaluate(
            SunReachResult sunReachResult,
            SkyVisibilityResult skyVisibilityResult,
            CanopyOcclusionResult canopyOcclusionResult,
            WeatherAttenuationResult weatherAttenuationResult,
            BiomeModifierResult biomeModifierResult
    ) {
        float solarTerrainFactor    = sunReachResult.value();
        float skyVisibilityFactor   = skyVisibilityResult.value();
        float canopyOcclusionFactor = canopyOcclusionResult.value();
        float weatherFactor         = weatherAttenuationResult.value();
        float biomeModifierFactor   = biomeModifierResult.biomeModifierFactor();

        // Appendix K §K.5–K.6: canonical formula, canonical order.
        // Stage Six (HumidityInteractionResult) intentionally excluded —
        // see class doc and Appendix K §K.4.
        float finalSunReach =
                solarTerrainFactor
                        * skyVisibilityFactor
                        * canopyOcclusionFactor
                        * weatherFactor
                        * biomeModifierFactor;

        return new SunReachCombinationResult(
                solarTerrainFactor,
                skyVisibilityFactor,
                canopyOcclusionFactor,
                weatherFactor,
                biomeModifierFactor,
                finalSunReach
        );
    }
}