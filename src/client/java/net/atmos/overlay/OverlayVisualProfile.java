package net.atmos.overlay;

import net.atmos.render.RenderColor;

/**
 * Pre-Batch 2 Overlay Foundation — dynamic tint parameters for the
 * universal grayscale mask/noise textures. No color is ever baked into a
 * texture; every visible color comes from this profile applied at render
 * time via vertex color multiplication.
 *
 * roughnessMultiplier and normalStrength exist only as forward-compatible
 * fields for a future PBR-style renderer path — unused by the current
 * OverlayRenderer, which is documented as out of scope here.
 */
public record OverlayVisualProfile(
        RenderColor colorTint,
        float opacity,
        float brightness,
        float contrast,
        float roughnessMultiplier,
        float normalStrength
) {
    public OverlayVisualProfile {
        if (colorTint == null) {
            throw new IllegalArgumentException("colorTint must not be null");
        }
    }
}