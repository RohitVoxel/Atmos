package net.atmos.render;

import net.minecraft.world.phys.Vec3;

/** Explainable output of Solar Direction Provider — Appendix ZB Blocker 7. */
public record SolarDirectionResult(
        float elevationRadians,
        float azimuthRadians,
        Vec3 direction
) {
    public SolarDirectionResult {
        if (direction == null) throw new IllegalArgumentException("direction must not be null");
    }
}