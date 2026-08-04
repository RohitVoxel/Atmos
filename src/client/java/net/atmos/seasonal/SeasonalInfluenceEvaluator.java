package net.atmos.seasonal;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Seasonal Influence evaluation — Phase 1.
 *
 *   temperatureInfluence = thermalTendency   (direct reuse, [-1,1])
 *   humidityInfluence    = moistureTendency  (direct reuse, [-1,1])
 *   daylightInfluence    = thermalTendency   (day length correlates with
 *                          warmth absent a separate solar-declination
 *                          model — a documented approximation, not a
 *                          duplicated ownership, since it answers a
 *                          different downstream question)
 *   windTendency         = volatility        ([0,1], already produced by
 *                          ContinuousBiasGenerator — highest during
 *                          transitional/equinox-like periods, matching
 *                          real-world seasonal windiness)
 *   weatherTendency       = wetter seasons and volatile transitions both
 *                          bias toward precipitation likelihood.
 */
public final class SeasonalInfluenceEvaluator {

    private SeasonalInfluenceEvaluator() {}

    private static final float WEATHER_MOISTURE_WEIGHT   = 0.6f;
    private static final float WEATHER_VOLATILITY_WEIGHT = 0.4f;

    public static SeasonalInfluenceResult evaluate(SeasonalProfileResult profile, SeasonalBiasResult bias) {
        float temperatureInfluence = profile.thermalTendency();
        float humidityInfluence    = profile.moistureTendency();
        float daylightInfluence    = profile.thermalTendency();
        float windTendency         = FogMath.clamp(bias.volatility(), 0f, 1f);

        float weatherRaw = 0.5f
                + WEATHER_MOISTURE_WEIGHT   * profile.moistureTendency() * 0.5f
                + WEATHER_VOLATILITY_WEIGHT * bias.volatility()          * 0.5f;
        float weatherTendency = FogMath.clamp(weatherRaw, 0f, 1f);

        return new SeasonalInfluenceResult(
                temperatureInfluence, humidityInfluence, daylightInfluence, windTendency, weatherTendency);
    }
}