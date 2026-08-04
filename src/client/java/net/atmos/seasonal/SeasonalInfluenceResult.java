package net.atmos.seasonal;

/**
 * Continuous seasonal influence channels — Phase 1. Downstream systems
 * (Fog, Cloud, Wind, Weather — none implemented yet) consume these
 * instead of deriving their own seasonal logic, per Seasonal Integration
 * Architecture.md.
 */
public record SeasonalInfluenceResult(
        float temperatureInfluence,
        float humidityInfluence,
        float daylightInfluence,
        float windTendency,
        float weatherTendency
) {
    public SeasonalInfluenceResult {
        requireSigned("temperatureInfluence", temperatureInfluence);
        requireSigned("humidityInfluence", humidityInfluence);
        requireSigned("daylightInfluence", daylightInfluence);
        requireUnit("windTendency", windTendency);
        requireUnit("weatherTendency", weatherTendency);
    }

    private static void requireSigned(String name, float v) {
        if (!Float.isFinite(v) || v < -1f || v > 1f) {
            throw new IllegalArgumentException(name + " must be within [-1,1], got " + v);
        }
    }

    private static void requireUnit(String name, float v) {
        if (!Float.isFinite(v) || v < 0f || v > 1f) {
            throw new IllegalArgumentException(name + " must be within [0,1], got " + v);
        }
    }

    public static SeasonalInfluenceResult neutral() {
        return new SeasonalInfluenceResult(0f, 0f, 0f, 0f, 0f);
    }
}