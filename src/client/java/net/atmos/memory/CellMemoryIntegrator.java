package net.atmos.memory;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;

/**
 * Per-cell Historical Data integration — Chapter 13 Stage 2 (Appendix F 2.0
 * §13.9), extended in Stage 4 with Adaptive Performance cadence scaling
 * (§13.18).
 *
 * Storage lives on {@link AtmosCell} itself (§13.9 — Historical Data
 * "must travel with [the cell]"); this class owns only the per-frame
 * advancement call.
 *
 * Converted from a static utility (Stage 2) to an instance in Stage 4:
 * cadence scaling requires a persistent accumulator, which a stateless
 * static method cannot hold. This class had no prior caller (documented
 * as unwired since Stage 2), so the shape change breaks nothing; it is
 * now wired into {@code AtmosClient} alongside {@code CellGrid}.
 *
 * Light Residue remains unimplemented — see Stage 2's original note;
 * unchanged in this pass.
 */
public final class CellMemoryIntegrator {

    private float accumulatedDeltaSec = 0f;

    /** Unscaled — equivalent to a null OptimizationPlan (budget = 1.0, advances every call). */
    public void update(CellGrid cellGrid, EnvironmentalState env, float deltaSec) {
        update(cellGrid, env, deltaSec, null);
    }

    /**
     * §13.18: reduced {@code optimizationPlan} budget lowers update
     * frequency only. Every applied update still invokes
     * {@link AtmosCell#advanceMemory} with full-precision math over the
     * full accumulated interval — mathematical precision is never
     * reduced, only sampling rate ("memory simply persists longer
     * between ticks").
     */
    public void update(CellGrid cellGrid, EnvironmentalState env, float deltaSec, OptimizationPlan optimizationPlan) {
        accumulatedDeltaSec += Math.max(0f, deltaSec);

        if (accumulatedDeltaSec < MemoryCadence.updateIntervalFor(optimizationPlan)) {
            return;
        }

        float appliedDeltaSec = accumulatedDeltaSec;
        accumulatedDeltaSec = 0f;

        long  tick           = cellGrid.currentTick();
        float humidityTarget = env.getHumidityMass();
        float stormTarget    = env.getStormEnergy();

        for (AtmosCell cell : cellGrid.getActiveCells()) {
            cell.advanceMemory(humidityTarget, stormTarget, appliedDeltaSec, tick);
        }
    }

    public void reset() {
        accumulatedDeltaSec = 0f;
    }
}