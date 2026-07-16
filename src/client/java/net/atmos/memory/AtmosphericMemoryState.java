package net.atmos.memory;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.AtmosphereDrifter;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Global Atmospheric Memory — Chapter 13 Stage 1 (§13.1–§13.8), extended
 * in Stage 4 with Adaptive Performance cadence scaling (§13.18).
 *
 * Owns deterministic decay of memory channels that lag behind
 * EnvironmentalState's already-smoothed output (§13.4, §13.7). Reuses
 * {@link AtmosphereDrifter} as the decay engine (Extend Before Creating).
 *
 * Implements Humidity Memory and Storm Memory only (§13.6). Light /
 * Exposure Memory are omitted — no canonical global illumination signal
 * exists yet (Chapter 6 §24 and Chapter 14 are both documented elsewhere
 * in this codebase as "not yet built").
 *
 * §13.8's strict saturation bound is enforced by clamping every advance()
 * to [0,1] — AtmosphereDrifter alone permits slight overshoot, which is
 * appropriate for EnvironmentalState but not for Memory.
 *
 * Ownership (§13.5): exclusive owner of this state. Publishes an immutable
 * {@link AtmosphericMemorySnapshot}; nothing downstream may mutate it.
 * Not wired into AtmosClient — this channel has no disk-persistence
 * requirement anywhere in Chapter 13 (only per-cell Historical Data does,
 * per §13.9) and remains an in-memory-only smoothing layer awaiting a
 * future consumer, unchanged from Stage 1.
 */
public final class AtmosphericMemoryState {

    private final AtmosphereDrifter humidityMemoryDrifter = new AtmosphereDrifter(
            MemoryWeights.GLOBAL_HUMIDITY_MEMORY_DEFAULT,
            MemoryWeights.GLOBAL_HUMIDITY_MEMORY_ACCEL,
            MemoryWeights.GLOBAL_HUMIDITY_MEMORY_DAMP);

    private final AtmosphereDrifter stormMemoryDrifter = new AtmosphereDrifter(
            MemoryWeights.GLOBAL_STORM_MEMORY_DEFAULT,
            MemoryWeights.GLOBAL_STORM_MEMORY_ACCEL,
            MemoryWeights.GLOBAL_STORM_MEMORY_DAMP);

    private float humidityMemory = MemoryWeights.GLOBAL_HUMIDITY_MEMORY_DEFAULT;
    private float stormMemory    = MemoryWeights.GLOBAL_STORM_MEMORY_DEFAULT;

    /** §13.18 cadence accumulator. Not part of the published snapshot. */
    private float accumulatedDeltaSec = 0f;

    /** Unscaled — equivalent to a null OptimizationPlan (budget = 1.0, advances every call). */
    public AtmosphericMemorySnapshot advance(EnvironmentalState env, float deltaSec) {
        return advance(env, deltaSec, null);
    }

    /**
     * §13.18: reduced {@code optimizationPlan} budget lowers update
     * frequency only. Every applied update still uses full-precision
     * {@link AtmosphereDrifter} math over the full accumulated interval —
     * mathematical precision is never reduced, only sampling rate.
     */
    public AtmosphericMemorySnapshot advance(EnvironmentalState env, float deltaSec, OptimizationPlan optimizationPlan) {
        accumulatedDeltaSec += Math.max(0f, deltaSec);

        if (accumulatedDeltaSec < MemoryCadence.updateIntervalFor(optimizationPlan)) {
            return new AtmosphericMemorySnapshot(humidityMemory, stormMemory);
        }

        float appliedDeltaSec = accumulatedDeltaSec;
        accumulatedDeltaSec = 0f;

        humidityMemory = FogMath.clamp(
                humidityMemoryDrifter.advance(env.getHumidityMass(), appliedDeltaSec), 0f, 1f);
        stormMemory = FogMath.clamp(
                stormMemoryDrifter.advance(env.getStormEnergy(), appliedDeltaSec), 0f, 1f);

        return new AtmosphericMemorySnapshot(humidityMemory, stormMemory);
    }

    /** Snaps both channels directly to current targets — avoids startup drift-in on first frame. */
    public void snapToTargets(EnvironmentalState env) {
        humidityMemory = env.getHumidityMass();
        stormMemory    = env.getStormEnergy();
        humidityMemoryDrifter.snap(humidityMemory);
        stormMemoryDrifter.snap(stormMemory);
    }

    public void reset() {
        humidityMemory = MemoryWeights.GLOBAL_HUMIDITY_MEMORY_DEFAULT;
        stormMemory    = MemoryWeights.GLOBAL_STORM_MEMORY_DEFAULT;
        humidityMemoryDrifter.snap(humidityMemory);
        stormMemoryDrifter.snap(stormMemory);
        accumulatedDeltaSec = 0f;
    }

    public float humidityMemory() { return humidityMemory; }
    public float stormMemory()    { return stormMemory;    }
}