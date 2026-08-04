package net.atmos.overlay;

import net.minecraft.core.BlockPos;

/** Identity of one (position, overlay type) accumulation transition tracked by OverlayLevelCrossingScheduler. */
public record SurfaceTransitionKey(BlockPos pos, OverlayType type) {}