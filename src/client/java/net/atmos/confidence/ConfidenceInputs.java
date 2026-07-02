package net.atmos.confidence;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.core.CameraSnapshot;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable bundle of everything one Confidence System evaluation needs.
 *
 * Deliberately a flat aggregate of already-existing, already-owned state —
 * per Chapter 2 §17-19 (Data Ownership), the Confidence System reads from
 * EnvironmentalState, the Cell Grid, and CameraSnapshot; it never owns or
 * mutates any of them.
 *
 * targetWorldPos is the world-space point being evaluated for Tier C
 * (e.g. a cell's center). No Cluster Builder exists yet to supply a
 * natural "candidate location," so callers must supply one explicitly —
 * this keeps ConfidenceInputs decoupled from any future system's shape.
 */
public record ConfidenceInputs(
        EnvironmentalState env,
        AtmosCell cell,
        CameraSnapshot camera,
        Vec3 targetWorldPos
) {
    public ConfidenceInputs {
        if (env == null)            throw new IllegalArgumentException("env must not be null");
        if (cell == null)           throw new IllegalArgumentException("cell must not be null");
        if (camera == null)         throw new IllegalArgumentException("camera must not be null");
        if (targetWorldPos == null) throw new IllegalArgumentException("targetWorldPos must not be null");
    }
}