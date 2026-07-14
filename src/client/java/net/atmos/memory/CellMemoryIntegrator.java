package net.atmos.memory;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;

/**
 * Per-cell Historical Data integration — Chapter 13 Stage 2 (Appendix F 2.0 §13.9).
 *
 * Advances every active {@link AtmosCell}'s Historical Memory toward the
 * current EnvironmentalState targets. Storage lives on AtmosCell itself
 * (§13.9: "stored directly within the local volumes managed by the Cell
 * Grid... must travel with [the cell]") — this class owns only the
 * per-frame advancement call.
 *
 * All active cells currently share the same global targets — no per-cell
 * environmental sampling exists yet (see AtmosCell's own class doc). The
 * meaningful per-cell distinction is temporal: a cell demoted to the cache
 * tier freezes at its last value instead of continuing to drift, and
 * resumes exactly where it left off on reactivation — satisfying §13.9's
 * "the world remembers its own unique history" without requiring per-cell
 * environmental sampling, which is out of scope here.
 *
 * Light Residue is not implemented — it would require invoking SunReach
 * (Chapter 8) per cell, ideally via the full Appendix K combination, whose
 * Canopy input (CanopyProfile) is not currently generated anywhere in Cell
 * Grid (CanopyProfileGenerator remains unwired). Deferred.
 *
 * Not wired into AtmosClient — awaiting a future integration task.
 */
public final class CellMemoryIntegrator {

    private CellMemoryIntegrator() {}

    public static void update(CellGrid cellGrid, EnvironmentalState env, float deltaSec) {
        long  tick           = cellGrid.currentTick();
        float humidityTarget = env.getHumidityMass();
        float stormTarget    = env.getStormEnergy();

        for (AtmosCell cell : cellGrid.getActiveCells()) {
            cell.advanceMemory(humidityTarget, stormTarget, deltaSec, tick);
        }
    }
}