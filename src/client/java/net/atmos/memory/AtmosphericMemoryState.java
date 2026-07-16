package net.atmos.memory;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.AtmosphereDrifter;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Global Atmospheric Memory — Chapter 13 Stage 1, extended Stage 4
 * (§13.18 cadence scaling) and Stage 5 (§13.17 failure recovery).
 *
 * §13.17: before any drifter is advanced, humidityMass/stormEnergy are
 * checked for finiteness. A non-finite reading (e.g. an upstream chapter
 * feeding an invalid value) is discarded for that cycle — the previous
 * valid memory state is retained rather than being permanently poisoned,
 * since AtmosphereDrifter itself has no NaN protection and this class
 * does not own or modify that Chapter-5 primitive.
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

    private float accumulatedDeltaSec = 0f;
    private long  invalidInputSkips   = 0L;

    public AtmosphericMemorySnapshot advance(EnvironmentalState env, float deltaSec) {
        return advance(env, deltaSec, null);
    }

    public AtmosphericMemorySnapshot advance(EnvironmentalState env, float deltaSec, OptimizationPlan optimizationPlan) {
        accumulatedDeltaSec += Math.max(0f, deltaSec);

        if (accumulatedDeltaSec < MemoryCadence.updateIntervalFor(optimizationPlan)) {
            return new AtmosphericMemorySnapshot(humidityMemory, stormMemory);
        }

        float appliedDeltaSec = accumulatedDeltaSec;
        accumulatedDeltaSec = 0f;

        float humidityTarget = env.getHumidityMass();
        float stormTarget    = env.getStormEnergy();
        if (!Float.isFinite(humidityTarget) || !Float.isFinite(stormTarget)) {
            invalidInputSkips++; // §13.17 — retain last valid memory state
            return new AtmosphericMemorySnapshot(humidityMemory, stormMemory);
        }

        humidityMemory = FogMath.clamp(
                humidityMemoryDrifter.advance(humidityTarget, appliedDeltaSec), 0f, 1f);
        stormMemory = FogMath.clamp(
                stormMemoryDrifter.advance(stormTarget, appliedDeltaSec), 0f, 1f);

        return new AtmosphericMemorySnapshot(humidityMemory, stormMemory);
    }

    public void snapToTargets(EnvironmentalState env) {
        float humidityTarget = env.getHumidityMass();
        float stormTarget    = env.getStormEnergy();
        if (!Float.isFinite(humidityTarget) || !Float.isFinite(stormTarget)) {
            invalidInputSkips++; // §13.17 — retain existing defaults
            return;
        }
        humidityMemory = humidityTarget;
        stormMemory    = stormTarget;
        humidityMemoryDrifter.snap(humidityMemory);
        stormMemoryDrifter.snap(stormMemory);
    }

    public void reset() {
        humidityMemory = MemoryWeights.GLOBAL_HUMIDITY_MEMORY_DEFAULT;
        stormMemory    = MemoryWeights.GLOBAL_STORM_MEMORY_DEFAULT;
        humidityMemoryDrifter.snap(humidityMemory);
        stormMemoryDrifter.snap(stormMemory);
        accumulatedDeltaSec = 0f;
        invalidInputSkips   = 0L;
    }

    public float humidityMemory() { return humidityMemory; }
    public float stormMemory()    { return stormMemory;    }

    /** Chapter 13 §13.17/§13.19 — count of cycles where a non-finite input was rejected. */
    public long invalidInputSkipCount() { return invalidInputSkips; }
}