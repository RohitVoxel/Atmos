package net.atmos.memory;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;

/**
 * Per-cell Historical Data integration — Chapter 13 Stage 2 (§13.9),
 * extended Stage 4 (§13.18 cadence scaling) and Stage 5 (§13.17 failure
 * recovery).
 *
 * §13.17: humidityTarget/stormTarget are checked for finiteness before
 * being applied to every active cell. Rejecting here — once per cycle —
 * is cheaper and more thorough than relying solely on AtmosCell's own
 * per-cell guard, and prevents a poisoned value from ever reaching disk
 * via a subsequent eviction write.
 */
public final class CellMemoryIntegrator {

    private float accumulatedDeltaSec = 0f;
    private long  invalidInputSkips   = 0L;

    public void update(CellGrid cellGrid, EnvironmentalState env, float deltaSec) {
        update(cellGrid, env, deltaSec, null);
    }

    public void update(CellGrid cellGrid, EnvironmentalState env, float deltaSec, OptimizationPlan optimizationPlan) {
        accumulatedDeltaSec += Math.max(0f, deltaSec);

        if (accumulatedDeltaSec < MemoryCadence.updateIntervalFor(optimizationPlan)) {
            return;
        }

        float appliedDeltaSec = accumulatedDeltaSec;
        accumulatedDeltaSec = 0f;

        float humidityTarget = env.getHumidityMass();
        float stormTarget    = env.getStormEnergy();
        if (!Float.isFinite(humidityTarget) || !Float.isFinite(stormTarget)) {
            invalidInputSkips++; // §13.17 — retain existing per-cell memory
            return;
        }

        long tick = cellGrid.currentTick();
        for (AtmosCell cell : cellGrid.getActiveCells()) {
            cell.advanceMemory(humidityTarget, stormTarget, appliedDeltaSec, tick);
        }
    }

    public void reset() {
        accumulatedDeltaSec = 0f;
        invalidInputSkips   = 0L;
    }

    /** Chapter 13 §13.17/§13.19 — count of cycles where a non-finite input was rejected. */
    public long invalidInputSkipCount() { return invalidInputSkips; }
}