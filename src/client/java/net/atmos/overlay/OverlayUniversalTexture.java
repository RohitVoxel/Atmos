package net.atmos.overlay;

import net.minecraft.resources.ResourceLocation;

/**
 * Pre-Batch 2 Overlay Foundation — fixed resource locations for the two
 * universal grayscale textures. Deliberately NOT discovered via
 * OverlayTextureDiscovery/OverlayTextureRegistry (that system remains
 * intact for legacy per-type/per-level diagnostics) — these two locations
 * are constant and always resolved through Minecraft's normal resource
 * pack -> Atmos user library -> built-in fallback order, since they are
 * ordinary registered textures.
 */
public final class OverlayUniversalTexture {

    private OverlayUniversalTexture() {}

    public static final ResourceLocation MASK =
            ResourceLocation.fromNamespaceAndPath("atmos", "textures/overlay/universal/overlay_mask.png");

    public static final ResourceLocation NOISE =
            ResourceLocation.fromNamespaceAndPath("atmos", "textures/overlay/universal/overlay_noise.png");
}