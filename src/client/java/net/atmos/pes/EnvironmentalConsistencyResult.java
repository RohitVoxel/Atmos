package net.atmos.pes;

/** Breakdown of one Environmental Consistency evaluation (§12.11). */
public record EnvironmentalConsistencyResult(
        float humidityDeviation,
        float value,
        boolean consistent
) {}