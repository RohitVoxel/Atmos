package net.atmos.aps;

/**
 * Memory-subsystem targets — Appendix D §2 ("MemoryOptimizationTargets:
 * Encapsulates { cacheLimits, memoryCadence }"). NEUTRAL mirrors
 * MemoryCadence's existing budget=1.0 -> interval=0 convention.
 */
public record MemoryOptimizationTargets(
        int cacheLimits,
        float memoryCadence
) {
    public static final MemoryOptimizationTargets NEUTRAL =
            new MemoryOptimizationTargets(Integer.MAX_VALUE, 0f);

    public MemoryOptimizationTargets {
        if (cacheLimits < 0) {
            throw new IllegalArgumentException("cacheLimits must be non-negative, got " + cacheLimits);
        }
        if (!Float.isFinite(memoryCadence) || memoryCadence < 0f) {
            throw new IllegalArgumentException("memoryCadence must be non-negative and finite, got " + memoryCadence);
        }
    }
}