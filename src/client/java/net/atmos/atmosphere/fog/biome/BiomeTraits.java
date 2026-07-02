package net.atmos.atmosphere.fog.biome;

public record BiomeTraits(
        float start,
        float end,
        float red,
        float green,
        float blue,
        float openness,
        float contrastRetention,
        float weatherSensitivity,
        float humidity             // 0.0 = arid, 1.0 = saturated
) {
    public static BiomeTraits of(float start, float end,
                                 float r, float g, float b,
                                 float openness,
                                 float contrastRetention,
                                 float weatherSensitivity,
                                 float humidity) {
        return new BiomeTraits(start, end, r, g, b,
                openness, contrastRetention, weatherSensitivity, humidity);
    }
}