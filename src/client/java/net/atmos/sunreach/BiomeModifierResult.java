package net.atmos.sunreach;

/**
 * Result of one Biome Modifier evaluation (Chapter 8 §16, Stage Seven).
 *
 * Per Appendix J §9, this is a single-field record — Stage Seven produces
 * exactly one continuous scalar with no secondary breakdown to preserve.
 * Unlike SunReachResult (Solar Position × Terrain) or WeatherAttenuationResult
 * (rain × thunder), Stage Seven has no internal sub-factors: it is a direct
 * linear interpretation of one BiomeTraits field. Adding extra fields here
 * purely for symmetry with other stage results would misrepresent what this
 * stage actually computes.
 */
public record BiomeModifierResult(
        float biomeModifierFactor
) {}