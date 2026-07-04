package net.atmos.sunreach;

/**
 * Weather Attenuation evaluator — Chapter 8 §14, Stage Five (Weather
 * Influence).
 *
 * --- Architecture status ---
 *
 * Chapter 8 §14 specifies only four qualitative anchor values for this
 * stage:
 *
 *     Clear         -> 1.00
 *     Rain          -> 0.72
 *     Heavy Rain    -> 0.48
 *     Thunderstorm  -> 0.22
 *
 * No transfer function, curve, or formula is given anywhere in Chapter 8 or
 * any Appendix (A through H) for how rain/thunder intensity maps onto these
 * anchors. This was confirmed by an explicit architecture search prior to
 * implementation. The transfer function is therefore intentionally
 * implementation-defined, exactly as Stage Four (Canopy Occlusion) treats
 * its own unspecified transfer function.
 *
 * Linear interpolation was selected over higher-order curves (e.g. a power
 * curve such as EnvironmentalState.rainIntensityCurve()'s rain^1.8 shape)
 * for two reasons:
 *
 *   1. It satisfies every architectural anchor exactly (see derivation
 *      below) without requiring any additional shaping parameter.
 *   2. EnvironmentalState.rainIntensityCurve() belongs to a different
 *      architectural responsibility — EnvironmentalState's stormEnergy is a
 *      deliberately lagging, emotionally-weighted atmospheric quantity
 *      (Chapter 3 §13), whereas Stage Five models direct, instantaneous
 *      illumination blocking (Chapter 8 §14). Reusing that curve here would
 *      couple two independent systems for no architectural benefit.
 *
 * --- What this stage does and does not do ---
 *
 * Per Chapter 8 §14: "weather reduces illumination — not atmospheric
 * density." This evaluator produces a pure illumination-reduction scalar.
 * It never reads, writes, or influences:
 *
 *   - EnvironmentalState (humidity, thermal energy, stormEnergy, etc.)
 *   - Fog density / scattering
 *   - Any Cell Grid, Confidence, or Renderer state
 *
 * --- Input signals ---
 *
 * Uses FogContext.rain() and FogContext.thunder() exclusively. Both are
 * already temporally smoothed at their source (FogManager applies
 * exponential smoothing before FogContext.capture() is called) — this
 * evaluator introduces no additional smoothing layer and does not read raw
 * Minecraft weather values.
 *
 * No defensive clamping is performed on these inputs. FogManager sources
 * both values from vanilla's level.getRainLevel(1.0f)/getThunderLevel(1.0f),
 * which are guaranteed to be in [0,1], and then smooths them via
 * FogMath.lerp() with a blend factor also in [0,1] — a convex combination of
 * two in-range values can never leave [0,1]. This mirrors the exact
 * precedent already established by TierAEvaluator, which reads
 * EnvironmentalState.humidityMass/thermalEnergy without re-clamping on the
 * documented basis that the upstream owner already guarantees the range.
 * Re-clamping here would be redundant defensive code duplicating a
 * guarantee this evaluator does not own.
 *
 * --- Derivation ---
 *
 * Rain attenuation is anchored at exactly two points:
 *
 *     rain = 0  -> 1.00           (Clear)
 *     rain = 1  -> HEAVY_RAIN_FACTOR (0.48)
 *
 * expressed as a single linear function:
 *
 *     rainAttenuation(rain) = 1 - (1 - HEAVY_RAIN_FACTOR) * rain
 *
 * This is not merely consistent with the Rain anchor (0.72) — solving for
 * the rain value at which this function equals 0.72 yields rain ≈ 0.538,
 * a moderate-to-firm rain intensity, which is the correct qualitative
 * territory for "Rain" as distinct from both light drizzle and Heavy Rain.
 *
 * Thunder attenuation is anchored so that, combined multiplicatively with
 * rain attenuation at rain=1, the Thunderstorm anchor (0.22) is reached
 * exactly:
 *
 *     thunderAttenuation(thunder) =
 *         1 - (1 - THUNDERSTORM_FACTOR / HEAVY_RAIN_FACTOR) * thunder
 *
 * At thunder=1: thunderAttenuation = THUNDERSTORM_FACTOR / HEAVY_RAIN_FACTOR
 * so that rainAttenuation(1) * thunderAttenuation(1) = HEAVY_RAIN_FACTOR *
 * (THUNDERSTORM_FACTOR / HEAVY_RAIN_FACTOR) = THUNDERSTORM_FACTOR exactly.
 *
 * Every coefficient in this class is derived directly from the named
 * architectural anchor constants below — no unexplained magic numbers are
 * hardcoded. If Chapter 8's anchor values are ever revised, only the two
 * named constants require updating; the formulas automatically re-derive
 * correctly around them.
 *
 * --- Combination ---
 *
 * combinedWeatherFactor = rainAttenuation * thunderAttenuation
 *
 * This is Stage Five's own internal combination only. It is NOT combined
 * with Stage One/Two/Four output in this evaluator — cross-stage
 * combination is a future task.
 *
 * Chapter 8 §17-18 establish multiplication as the general combination
 * philosophy ACROSS SunReach stages (Solar Position x Terrain x Sky
 * Visibility x Canopy x Weather x Biome) — justified there as modeling
 * proportional environmental attenuation, where any single obstruction can
 * legitimately drive the whole result toward zero. That principle governs
 * cross-stage combination, not the internal decomposition of a single
 * stage. Chapter 8 §14 never splits "Weather Influence" into a rain-factor
 * and a thunder-factor in the first place — it gives only combined-weather
 * anchors. The decision to decompose Stage Five into two sub-factors and
 * recombine them via multiplication is therefore implementation-defined,
 * not literally mandated by the anchors — the same category of decision as
 * Stage Four's transfer function. Multiplication was chosen here because it
 * is consistent with the architecture's general attenuation philosophy
 * (§17-18) and because it is the only operator that reproduces the
 * Thunderstorm anchor exactly from the Heavy Rain and Thunderstorm anchors
 * without introducing a third free parameter (see derivation above).
 *
 * --- Determinism, threading, performance ---
 *
 * Stateless, side-effect-free, O(1): two subtractions, three
 * multiplications. No defensive clamping (see Input signals above), no
 * caching, no allocation beyond the returned WeatherAttenuationResult, no
 * world queries, no GPU interaction. Safe for Simulation Thread use per
 * Appendix D §11 — no mutable static state exists in this class.
 */
public final class WeatherAttenuationEvaluator {

    private WeatherAttenuationEvaluator() {}

    // --- Chapter 8 §14 architectural anchors ---
    // Clear is implicitly 1.00f and does not need its own constant — it is
    // the natural result of both attenuation functions at zero input.
    private static final float HEAVY_RAIN_FACTOR   = 0.48f;
    private static final float THUNDERSTORM_FACTOR = 0.22f;

    public static WeatherAttenuationResult evaluate(float rain, float thunder) {
        // No clamping: FogContext.rain()/thunder() are guaranteed in [0,1]
        // by FogManager's construction — see Input signals above.
        float rainAttenuation = 1f - (1f - HEAVY_RAIN_FACTOR) * rain;

        float thunderAttenuation =
                1f - (1f - THUNDERSTORM_FACTOR / HEAVY_RAIN_FACTOR) * thunder;

        float combinedWeatherFactor = rainAttenuation * thunderAttenuation;

        return new WeatherAttenuationResult(rainAttenuation, thunderAttenuation, combinedWeatherFactor);
    }
}