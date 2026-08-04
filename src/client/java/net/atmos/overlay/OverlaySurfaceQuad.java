package net.atmos.overlay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public record OverlaySurfaceQuad(
        BlockPos origin,
        Direction face,
        int extentA,
        int extentB,
        BlockState representativeState,
        float exposure,
        float temperature,
        float humidity,
        float rainfall,
        int cachedLight
) {
    public OverlaySurfaceQuad {
        if (origin == null) throw new IllegalArgumentException("origin must not be null");
        if (face == null) throw new IllegalArgumentException("face must not be null");
        if (representativeState == null) throw new IllegalArgumentException("representativeState must not be null");
        if (extentA <= 0) throw new IllegalArgumentException("extentA must be positive, got " + extentA);
        if (extentB <= 0) throw new IllegalArgumentException("extentB must be positive, got " + extentB);
    }

    /** Rebuild cadence only — never called per frame. See ChunkSurfaceIndex. */
    public OverlaySurfaceQuad withLight(int light) {
        return new OverlaySurfaceQuad(origin, face, extentA, extentB,
                representativeState, exposure, temperature, humidity, rainfall, light);
    }

    public int widthBlocks() { return extentA; }
    public int depthBlocks() { return extentB; }

    // Future requirement: for very large merged quads, cachedLight() may need
    // to become an array of corner samples rather than one representative
    // value. If that happens, ChunkSurfaceIndex.buildMergedQuads is the only
    // call site that would change — OverlayRenderer.emitQuad already takes
    // the light value as a parameter and would just read 4 instead of 1.

}