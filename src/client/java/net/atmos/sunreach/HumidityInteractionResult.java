package net.atmos.sunreach;

/**
 * Result of one Humidity Interaction evaluation (Chapter 8 §15, Stage Six).
 *
 * Per Appendix I §4, this is a single-field record, not a composite of
 * sub-factors. Chapter 8 §15 describes Stage Six as one continuous scattering-
 * efficiency signal derived from humidity alone — there is no secondary
 * breakdown to preserve, unlike SunReachResult (Solar Position × Terrain) or
 * WeatherAttenuationResult (rain × thunder). Adding extra fields here purely
 * for symmetry with other stage results would misrepresent what this stage
 * actually computes.
 *
 * humidityFactor represents scattering efficiency only — see
 * HumidityInteractionEvaluator's class doc for why this is architecturally
 * distinct from SunReach's light-availability factors, and why this value is
 * never multiplied into SunReachResult.value.
 */
public record HumidityInteractionResult(
        float humidityFactor
) {}