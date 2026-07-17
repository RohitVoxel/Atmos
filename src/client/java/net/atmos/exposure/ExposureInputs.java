package net.atmos.exposure;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.CellGrid;
import net.atmos.composition.Composition;
import net.atmos.director.DirectorState;
import net.atmos.memory.AtmosphericMemorySnapshot;

/**
 * Immutable bundle of every upstream producer the Exposure Model consumes,
 * per Chapter 14 §14.4's convergence-point pipeline: EnvironmentalState ->
 * CellGrid -> AtmosphericMemory -> Composition -> AtmosphereDirector ->
 * OptimizationPlan -> Exposure Model.
 *
 * env and cellGrid are read directly rather than through a snapshot
 * wrapper — both are Simulation-Thread-owned mutable state (Appendix D
 * §11) and the Exposure Model itself executes exclusively on the
 * Simulation Thread (§14.12), so same-thread access requires no
 * intermediate snapshot type. Matches the existing precedent of
 * {@code ClusterBuilder.build(CellGrid, EnvironmentalState)}.
 *
 * memory and optimizationPlan are nullable: neither producer is
 * unconditionally available (AtmosphericMemoryState's global channel is
 * unwired into the live loop; Chapter 16 / APS is unimplemented). Both
 * follow the identical nullable-with-neutral-fallback precedent already
 * established by {@code MemoryEvaluator} and
 * {@code DirectorPerformanceEvaluator}.
 *
 * SunReach is intentionally absent. No single SunReach aggregate exists
 * at this pipeline position — Chapter 8 produces per-cell/per-cluster
 * results, and RenderCluster (Appendix L) is where a finalized per-cluster
 * {@code sunReach} is eventually attached, downstream of the Exposure
 * Model. Identical omission already documented by {@code CompositionInputs}.
 */
public record ExposureInputs(
        EnvironmentalState env,
        CellGrid cellGrid,
        AtmosphericMemorySnapshot memory,
        Composition composition,
        DirectorState directorState,
        OptimizationPlan optimizationPlan
) {
    public ExposureInputs {
        if (env == null) throw new IllegalArgumentException("env must not be null");
        if (cellGrid == null) throw new IllegalArgumentException("cellGrid must not be null");
        if (composition == null) throw new IllegalArgumentException("composition must not be null");
        if (directorState == null) throw new IllegalArgumentException("directorState must not be null");
        // memory, optimizationPlan: nullable — see class doc.
    }
}