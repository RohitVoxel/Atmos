package net.atmos.memory;

import net.atmos.cellgrid.CellGrid;

/**
 * Aggregate developer diagnostics for Atmospheric Memory — Chapter 13 §13.19.
 *
 * Combines global channel state (§13.10), per-cell activity (§13.9, via
 * CellGrid), and persistence health (§13.12–§13.16) into one read-only
 * snapshot. Data only — no overlay UI exists yet, matching the existing
 * {@link MemoryDiagnostics} precedent.
 *
 * globalMemory may be null: AtmosphericMemoryState remains intentionally
 * unwired into the live game loop (documented since Stage 1 — no
 * disk-persistence requirement exists for the global channel). Global
 * fields default to 0/0f when absent rather than failing, mirroring the
 * null-OptimizationPlan idiom already used by MemoryCadence and
 * DirectorPerformanceEvaluator.
 *
 * *InvalidInputSkips (§13.17): count of update cycles where a non-finite
 * EnvironmentalState reading was detected and discarded before reaching
 * any drifter — the recovery-statistics signal requested by Stage 6.
 *
 * Temperature Memory and Light Memory are not represented: no such
 * channel exists anywhere in Chapter 13's implementation (Light Residue
 * was explicitly deferred in Stage 1/2; Thermal energy is an immediate
 * EnvironmentalState value, never a memory channel). No placeholder
 * fields are added for them.
 */
public record AtmosphericMemoryDiagnostics(
        float globalHumidityMemory,
        float globalStormMemory,
        long globalInvalidInputSkips,
        int activeMemoryCells,
        long cellInvalidInputSkips,
        MemoryDiagnostics persistence
) {
    public static AtmosphericMemoryDiagnostics capture(
            AtmosphericMemoryState globalMemory,
            CellMemoryIntegrator cellIntegrator,
            CellGrid cellGrid) {

        float globalHumidity = globalMemory != null ? globalMemory.humidityMemory() : 0f;
        float globalStorm    = globalMemory != null ? globalMemory.stormMemory()    : 0f;
        long  globalSkips    = globalMemory != null ? globalMemory.invalidInputSkipCount() : 0L;

        return new AtmosphericMemoryDiagnostics(
                globalHumidity,
                globalStorm,
                globalSkips,
                cellGrid.getActiveCells().size(),
                cellIntegrator.invalidInputSkipCount(),
                cellGrid.memoryDiagnostics()
        );
    }
}