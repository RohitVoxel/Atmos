package net.atmos.overlay;

/**
 * One overlay surface's accumulation transition, on the Atmos overlay scale
 * (0.0 -> 10.0, not 0-1). Simulation publishes a new transition only when
 * the desired target changes meaningfully; the renderer (or any other
 * consumer) calls interpolate() to get the current smoothly-eased value
 * for a given tick, with zero CPU cost on the simulation side between
 * transitions.
 *
 * durationTicks is always >= 1 to avoid division by zero; a transition
 * with durationTicks == 1 behaves as an immediate snap.
 */
public record OverlaySurfaceValue(
        float startValue,
        float targetValue,
        long startTick,
        long durationTicks
) {
    public static final OverlaySurfaceValue ZERO = new OverlaySurfaceValue(0f, 0f, 0L, 1L);

    public OverlaySurfaceValue {
        if (durationTicks < 1L) durationTicks = 1L;
    }

    /** Eased value at currentTick. Never mutates; pure function of the transition. */
    public float interpolate(long currentTick) {
        long elapsed = currentTick - startTick;
        if (elapsed <= 0L) return startValue;
        if (elapsed >= durationTicks) return targetValue;

        float t = (float) elapsed / (float) durationTicks;
        float eased = t * t * (3f - 2f * t); // smoothstep
        return startValue + (targetValue - startValue) * eased;
    }
}