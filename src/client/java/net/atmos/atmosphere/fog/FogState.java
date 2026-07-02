package net.atmos.atmosphere.fog;

public record FogState(
        float start,
        float end,
        float red,
        float green,
        float blue,
        float openness,
        float contrastRetention,
        float weatherSensitivity,
        float humidity
) {
    public FogState withDistances(float newStart, float newEnd) {
        return new FogState(newStart, newEnd, red, green, blue,
                openness, contrastRetention, weatherSensitivity, humidity);
    }

    public FogState withColor(float r, float g, float b) {
        return new FogState(start, end, r, g, b,
                openness, contrastRetention, weatherSensitivity, humidity);
    }

    public FogState with(float newStart, float newEnd, float r, float g, float b) {
        return new FogState(newStart, newEnd, r, g, b,
                openness, contrastRetention, weatherSensitivity, humidity);
    }
}