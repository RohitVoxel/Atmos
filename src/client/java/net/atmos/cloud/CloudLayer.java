package net.atmos.cloud;

import net.minecraft.resources.ResourceLocation;

/**
 * One immutable render layer. Batch 1 fields are rendering information only —
 * no velocity, no simulation state. {@code texture} is null and
 * {@code enabled} is false when no texture family could be resolved during
 * initialization (e.g. missing resource pack assets).
 */
public record CloudLayer(
        int index,
        CloudLayerType type,
        float height,
        float scale,
        float opacity,
        String textureFamily,
        ResourceLocation texture,
        boolean enabled
) {
    public CloudLayer {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative, got " + index);
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (scale <= 0f) {
            throw new IllegalArgumentException("scale must be positive, got " + scale);
        }
        if (opacity < 0f || opacity > 1f) {
            throw new IllegalArgumentException("opacity must be within [0,1], got " + opacity);
        }
        if (textureFamily == null || textureFamily.isEmpty()) {
            throw new IllegalArgumentException("textureFamily must not be null or empty");
        }
    }
}
