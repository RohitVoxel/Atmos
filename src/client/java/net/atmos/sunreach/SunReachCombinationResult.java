package net.atmos.sunreach;

/**
 * Explainable result of the Final SunReach Combination (Chapter 8 §17–§18,
 * fully specified by Appendix K).
 *
 * Per Appendix K §K.11, this result preserves both the individual stage
 * values that fed the combination and the combined scalar itself, so a
 * future debug overlay can render the full breakdown without recomputation
 * — the identical explainability pattern already established by
 * {@link SunReachResult}, {@link CanopyOcclusionResult},
 * {@link WeatherAttenuationResult}, and {@link BiomeModifierResult}.
 *
 * This is a dedicated record rather than an extension of
 * {@link SunReachResult}. SunReachResult's own class doc scopes it
 * explicitly to Stages One and Two only ("Only this task's two stages are
 * represented... Later Chapter 8 tasks will extend this record
 * additively") — extending it here would misattribute Stage Three through
 * Seven data as belonging to the Stage One/Two evaluator's result type.
 * A standalone record matches the pattern already used by every other
 * stage's result type in this package.
 *
 * Fields:
 *
 *   solarTerrainFactor — {@link SunReachResult#value()} (Stages One–Two:
 *                        Solar Position × Terrain Exposure, already
 *                        combined internally by SunReachEvaluator).
 *   skyVisibilityFactor — {@link SkyVisibilityResult#value()} (Stage Three).
 *   canopyOcclusionFactor — {@link CanopyOcclusionResult#value()} (Stage Four).
 *   weatherFactor — {@link WeatherAttenuationResult#value()} (Stage Five).
 *   biomeModifierFactor — {@link BiomeModifierResult#biomeModifierFactor()}
 *                         (Stage Seven).
 *   finalSunReach — the combined result: the product of all five factors
 *                   above, per Appendix K §K.5.
 *
 * Stage Six (Humidity Interaction) is deliberately absent from both this
 * record and the combination formula. Per Appendix K §K.4, humidity
 * represents scattering efficiency, not sunlight availability, and must
 * never reduce or increase SunReach itself. HumidityInteractionResult
 * remains an independent sibling signal, never referenced here.
 *
 * Expected range: [0.0, 1.10] per Appendix K §K.8 — the upper bound of
 * 1.10 arises solely from BiomeModifierResult's maximum enhancement
 * factor; no clamping is applied beyond ordinary floating-point behavior,
 * per Appendix K §K.9 ("no hidden logic... no clamping beyond
 * mathematically impossible floating-point safety").
 */
public record SunReachCombinationResult(
        float solarTerrainFactor,
        float skyVisibilityFactor,
        float canopyOcclusionFactor,
        float weatherFactor,
        float biomeModifierFactor,
        float finalSunReach
) {}