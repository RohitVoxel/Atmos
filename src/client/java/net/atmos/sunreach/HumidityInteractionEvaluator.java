package net.atmos.sunreach;

/**
 * Humidity Interaction evaluator — Chapter 8 §15, Stage Six.
 *
 * Per Appendix I §1: this stage does not represent atmospheric density and
 * does not represent sunlight attenuation. It represents exactly one thing —
 * the efficiency with which sunlight that is already present can produce
 * visible atmospheric scattering. It answers "if sunlight already exists
 * here, how effectively can atmospheric moisture reveal it?", never "how
 * much sunlight exists here?" (that question belongs entirely to
 * SunReachEvaluator's Stages One and Two).
 *
 * --- Ownership (Appendix I §2) ---
 *
 * This evaluator belongs exclusively to the sunreach package. It is a sibling
 * to SunReachEvaluator, CanopyOcclusionEvaluator, SkyVisibilityEvaluator, and
 * WeatherAttenuationEvaluator — not a subordinate of any of them. It does not
 * belong to EnvironmentalState, the Cell Grid, the Confidence System, or the
 * renderer.
 *
 * EnvironmentalState owns humidity measurement (humidityMass). This evaluator
 * owns humidity's *interpretation* for cinematic sunlight scattering only —
 * the same relationship TierAEvaluator has to thermalEnergy for confidence
 * gating, and a completely independent one. Multiple readers, one owner, no
 * ownership conflict (Appendix I §7).
 *
 * --- Input (Appendix I §3) ---
 *
 * Consumes only EnvironmentalState.humidityMass, supplied here as a primitive
 * float by the caller — matching the established sunreach-package idiom
 * already used by SunReachEvaluator.evaluate(HorizonMap, float) and
 * WeatherAttenuationEvaluator.evaluate(float, float): evaluators in this
 * package take primitives already extracted by the caller rather than
 * depending on the owning state class directly.
 *
 * No other atmospheric variable participates: not thermalEnergy, stormEnergy,
 * rainLevel, thunderLevel, cloud state, fog density, the Cell Grid, HorizonMap,
 * or CanopyProfile. Those belong to other stages.
 *
 * No re-clamping is performed here. EnvironmentalState.advance() already
 * clamps humidityMass to [0,1] via FogMath.clamp() on humidityMassDrifter —
 * the identical precedent already documented in TierAEvaluator and
 * WeatherAttenuationEvaluator ("Input signals... already smoothed/clamped at
 * their source"). Re-clamping here would duplicate a guarantee this evaluator
 * does not own.
 *
 * --- Transfer function (Appendix I §8) ---
 *
 * Chapter 8 §15 supplies no numeric anchors for this stage — only two
 * qualitative directional examples:
 *
 *     humidity = 0.00, SunReach = 1.00  ->  "Very Thin Shafts"
 *     humidity = 1.00, SunReach = 0.05  ->  "Almost No Shafts"
 *
 * (both examples illustrate that SunReach gates the ceiling; neither supplies
 * a curve). This is architecturally different from Stage Five, which had four
 * concrete anchors (Clear/Rain/Heavy Rain/Thunderstorm) from which a linear
 * attenuation could be derived exactly. No equivalent anchors exist here.
 *
 * Appendix I §8 therefore leaves the transfer function implementation-defined,
 * constrained only to be:
 *   - continuous
 *   - deterministic
 *   - monotonic
 *   - bounded within [0,1]
 *   - humidity=0 -> no scattering enhancement
 *   - humidity=1 -> maximum scattering efficiency
 *
 * Identity (humidityFactor = humidityMass) is the mapping used here. It is the
 * only function satisfying every constraint above without introducing an
 * unstated coefficient, saturation curve, or shaping exponent that Chapter 8
 * never specifies anywhere. Per the Permanent Coder Instructions' "no silent
 * assumptions" rule, a shaped curve (e.g. exponential saturation, as used in
 * DepthFogModifier or CanopyOcclusionEvaluator) would require an independently
 * justified constant this chapter does not provide — identity requires none.
 *
 * --- Stage combination (Appendix I §5, §6, §9) ---
 *
 * This result is NOT multiplied into SunReachResult.value. Chapter 8 §17's
 * combined formula (Solar Position × Terrain × Sky Visibility × Canopy ×
 * Weather × Biome Modifier) has no Humidity term — this is not an omission,
 * per Appendix I §5: humidity does not block sunlight, so it must never
 * reduce SunReach. HumidityInteractionResult exists as an independent sibling
 * signal until a future Composition/rendering stage — outside Chapter 8 —
 * chooses to combine it with SunReach. No such combination is anticipated or
 * placeholder-wired here.
 *
 * --- Threading, determinism, performance (Appendix I §10) ---
 *
 * Stateless, side-effect-free, O(1): one field copy, no allocation beyond the
 * returned record, no caching, no world access, no mutable static state.
 * Deterministic — identical humidityMass always produces an identical result.
 * Safe for Simulation Thread use, matching every other sunreach evaluator.
 *
 * --- Task boundary ---
 *
 * Stage Six only. Not wired into SunReachEvaluator, AtmosClient, FogManager,
 * the Confidence System, or any renderer. Matches the same "inert, standalone"
 * pattern already established by CanopyOcclusionEvaluator and
 * WeatherAttenuationEvaluator — both exist today with no call site, awaiting
 * a future integration task that is explicitly out of scope here.
 */
public final class HumidityInteractionEvaluator {

    private HumidityInteractionEvaluator() {}

    public static HumidityInteractionResult evaluate(float humidityMass) {
        return new HumidityInteractionResult(humidityFactor(humidityMass));
    }

    private static float humidityFactor(float humidityMass) {
        // Identity mapping — see class doc for why this is the only
        // architecturally justifiable transfer function given Chapter 8 §15's
        // lack of numeric anchors.
        return humidityMass;
    }
}