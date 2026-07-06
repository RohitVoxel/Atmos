package net.atmos.sunreach;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.atmosphere.fog.biome.BiomeTraits;

/**
 * Biome Modifier evaluator — Chapter 8 §16, Stage Seven.
 *
 * Per Appendix J, this stage represents how biome-specific environmental
 * character subtly influences the visual effectiveness of sunlight that has
 * already reached the atmosphere. It does not measure sunlight availability
 * (SunReachEvaluator's responsibility) and does not measure atmospheric
 * density or scattering efficiency (HumidityInteractionEvaluator's
 * responsibility, Chapter 8 §15). It answers exactly one question:
 *
 *     "Given the same available sunlight, should this biome naturally
 *      reveal that sunlight slightly more or slightly less effectively?"
 *
 * --- Ownership (Appendix J §2) ---
 *
 * This evaluator belongs exclusively to the sunreach package, as a sibling
 * to SunReachEvaluator, SkyVisibilityEvaluator, CanopyOcclusionEvaluator,
 * WeatherAttenuationEvaluator, and HumidityInteractionEvaluator. It does not
 * belong to EnvironmentalState or BiomeAtmosphereRegistry.
 *
 * BiomeAtmosphereRegistry owns biome classification and BiomeTraits
 * construction (Appendix J §2, §4). This evaluator owns only the
 * interpretation of one already-resolved BiomeTraits field for SunReach
 * purposes — it never performs biome lookup, never queries the world, and
 * never maintains an independent biome database (Appendix J §3, §4).
 *
 * --- Input (Appendix J §3, §5) ---
 *
 * Consumes only BiomeTraits.humidity() — no other BiomeTraits field
 * participates (not openness, weatherSensitivity, contrastRetention, or fog
 * color), and no other atmospheric variable participates (not
 * EnvironmentalState, not Cell Grid, not HorizonMap, not CanopyProfile).
 * Humidity alone represents the long-term atmospheric tendency of the biome
 * (Appendix J §5).
 *
 * No re-clamping is performed here. Every BiomeTraits.humidity() value
 * defined in BiomeAtmosphereRegistry already falls within [0,1] by
 * construction — the identical precedent already documented in
 * TierAEvaluator and WeatherAttenuationEvaluator ("input signals already
 * guaranteed in range by their upstream owner"). Re-clamping here would
 * duplicate a guarantee this evaluator does not own.
 *
 * --- Transfer function (Appendix J §6, §7, §8) ---
 *
 * Chapter 8 §16 supplies no numeric anchors for this stage — only three
 * qualitative examples (Snow/Desert: slight increase, Swamp: slight
 * reduction) and a requirement that biome influence "remain subtle." Per
 * Appendix J §6, §7, the canonical transfer function is fixed as:
 *
 *     BiomeModifierFactor = lerp(1.10, 0.90, humidity)
 *
 *     humidity = 0.00  ->  1.10  (strongest enhancement)
 *     humidity = 0.50  ->  1.00  (neutral)
 *     humidity = 1.00  ->  0.90  (strongest reduction)
 *
 * This reuses FogMath.lerp() rather than duplicating linear interpolation
 * logic — the identical utility already used by SunReachEvaluator and
 * SkyVisibilityEvaluator within this package (Permanent Instructions:
 * "Extend Before Creating"). Per Appendix J §8, linear interpolation is the
 * only transfer function requiring zero invented coefficients, since no
 * numeric anchors exist to justify a power curve, logistic, smoothstep, or
 * exponential shape.
 *
 * The [0.90, 1.10] output range is fixed by Appendix J §6 and is not
 * configurable by this evaluator — it is the architectural definition of
 * "subtle" for this stage.
 *
 * --- Stage combination (Appendix J §10) ---
 *
 * This result is NOT combined with SunReachResult.value, and this evaluator
 * introduces no call sites into SunReachEvaluator or any other system.
 * BiomeModifierResult exists as an independent standalone sibling signal.
 * Per Appendix J §10, integration occurs only when a future chapter
 * explicitly reaches SunReach's final combination task — no such
 * integration is anticipated or placeholder-wired here.
 *
 * --- Threading, determinism, performance (Appendix J §11, §12) ---
 *
 * Stateless, side-effect-free, O(1): one field read, one lerp call, no
 * allocation beyond the returned record, no caching, no world access, no
 * registry lookup, no mutable static state. Deterministic — identical
 * BiomeTraits always produce an identical result. Safe for Simulation
 * Thread use, matching every other sunreach evaluator.
 *
 * --- Task boundary ---
 *
 * Stage Seven only. Not wired into SunReachEvaluator, AtmosClient,
 * FogManager, the Confidence System, Cell Grid, or any renderer — matching
 * the same "inert, standalone" pattern already established by
 * CanopyOcclusionEvaluator, WeatherAttenuationEvaluator, and
 * HumidityInteractionEvaluator, all of which exist today with no call site,
 * awaiting a future integration task explicitly out of scope here.
 */
public final class BiomeModifierEvaluator {

    private BiomeModifierEvaluator() {}

    // Fixed output bounds per Appendix J §6 — the architectural definition
    // of "subtle" biome influence for this stage. Not configurable.
    private static final float MAX_ENHANCEMENT = 1.10f;
    private static final float MAX_REDUCTION   = 0.90f;

    public static BiomeModifierResult evaluate(BiomeTraits traits) {
        float humidity = traits.humidity();

        // Appendix J §7: BiomeModifierFactor = lerp(1.10, 0.90, humidity).
        float biomeModifierFactor = FogMath.lerp(MAX_ENHANCEMENT, MAX_REDUCTION, humidity);

        return new BiomeModifierResult(biomeModifierFactor);
    }
}