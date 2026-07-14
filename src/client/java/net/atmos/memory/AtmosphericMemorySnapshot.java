package net.atmos.memory;

/**
 * Immutable global Atmospheric Memory snapshot — Chapter 13 §13.5.
 * Published once per {@link AtmosphericMemoryState#advance}. Read-only for
 * every consumer; never written back to. Name matches Chapter 14's own
 * forward-referenced pipeline input type of the same name.
 */
public record AtmosphericMemorySnapshot(
        float humidityMemory,
        float stormMemory
) {
    public AtmosphericMemorySnapshot {
        if (!Float.isFinite(humidityMemory) || humidityMemory < 0f || humidityMemory > 1f) {
            throw new IllegalArgumentException(
                    "humidityMemory must be within [0,1], got " + humidityMemory);
        }
        if (!Float.isFinite(stormMemory) || stormMemory < 0f || stormMemory > 1f) {
            throw new IllegalArgumentException(
                    "stormMemory must be within [0,1], got " + stormMemory);
        }
    }
}