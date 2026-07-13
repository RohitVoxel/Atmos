package net.atmos.pes;

/** Breakdown of one Biome Identity evaluation (§12.12). */
public record BiomeIdentityResult(
        float humidityIdentityDeviation,
        float value,
        boolean consistent
) {}