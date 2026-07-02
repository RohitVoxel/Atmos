package net.atmos.atmosphere.fog.biome;

/**
 * Full atmospheric profile for a biome.
 * Currently wraps fog traits only. Future systems (sky tint, particles,
 * ambience) attach here without touching the registry or pipeline.
 */
public record BiomeAtmosphere(BiomeTraits fog) {

    public static BiomeAtmosphere of(BiomeTraits fog) {
        return new BiomeAtmosphere(fog);
    }
}