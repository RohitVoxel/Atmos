package net.atmos.pes;

/** Breakdown of one Memory Evaluation (§12.24). */
public record MemoryEvaluationResult(
        float humidityMemoryDeviation,
        float stormMemoryDeviation,
        float value,
        boolean consistent
) {}