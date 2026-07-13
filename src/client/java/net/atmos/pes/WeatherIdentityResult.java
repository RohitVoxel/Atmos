package net.atmos.pes;

/** Breakdown of one Weather Identity evaluation (§12.13). */
public record WeatherIdentityResult(
        float stormDensityDeviation,
        float value,
        boolean consistent
) {}