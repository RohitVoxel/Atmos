package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;
import net.minecraft.core.BlockPos;

/**
 * Per-surface overlay target generation — event-driven transition publisher.
 * Batch 3: threads the exposed-face bitmask through to
 * OverlaySurfaceStateStore.setTarget so level crossings can be resolved
 * into the correct (chunk, face) invalidation keys without ChunkSurfaceIndex
 * ever being consulted again at crossing time.
 */
public final class OverlayAccumulationSimulation {

    private static final float TRANSITION_TICKS_SCALAR = 26.4f;
    private static final long MIN_DURATION_TICKS = 20L;
    private static final long MAX_DURATION_TICKS = 12000L;

    private final OverlaySurfaceStateStore store;
    private final OverlayManager overlayManager;

    public OverlayAccumulationSimulation(OverlaySurfaceStateStore store, OverlayManager overlayManager) {
        this.store = store;
        this.overlayManager = overlayManager;
    }

    public void advance(OverlaySurface surface, int faceMask, OverlayMaterial material,
                        OverlayEnvironmentalContext ctx, long currentTick) {
        if (material == OverlayMaterial.NONE) return;

        BlockPos pos = surface.pos();

        publish(pos, faceMask, material, OverlayType.SNOW, currentTick,
                OverlaySnowBehavior.evaluate(surface, overlayManager.getValue(OverlayType.SNOW), ctx));

        publish(pos, faceMask, material, OverlayType.FROST, currentTick,
                OverlayFrostBehavior.evaluate(surface, ctx));

        publish(pos, faceMask, material, OverlayType.WET, currentTick,
                OverlayWetnessBehavior.evaluate(surface, ctx));

        publish(pos, faceMask, material, OverlayType.DUST, currentTick,
                OverlayDustBehavior.evaluate(surface, ctx));

        publish(pos, faceMask, material, OverlayType.POLLEN, currentTick,
                OverlayPollenBehavior.evaluate(surface, overlayManager.getValue(OverlayType.POLLEN), ctx));
    }

    private void publish(BlockPos pos, int faceMask, OverlayMaterial material, OverlayType type, long currentTick,
                         OverlayTargetResult result) {
        float desiredTarget = result.desiredTarget();
        float storedTarget = store.storedTarget(pos, type);
        boolean rising = desiredTarget >= storedTarget;

        float rate = rising
                ? OverlayMaterialProfile.buildRate(material, type)
                : OverlayMaterialProfile.clearRate(material, type);
        rate *= result.rateMultiplier();
        rate = Math.max(rate, 0.001f);

        long durationTicks = (long) FogMath.clamp(
                TRANSITION_TICKS_SCALAR / rate, MIN_DURATION_TICKS, MAX_DURATION_TICKS);

        store.setTarget(pos, type, desiredTarget, currentTick, durationTicks, faceMask);
    }
}