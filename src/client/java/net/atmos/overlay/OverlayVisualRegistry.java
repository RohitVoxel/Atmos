package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.render.RenderColor;

/**
 * Pre-Batch 2 Overlay Foundation — deterministic, level-driven tint
 * resolution for every OverlayType. Replaces per-type/per-level baked
 * texture files with a single tint function evaluated at render time.
 *
 * The level-based brightness curve below is a deterministic function of
 * the existing OverlayLevelResolver level (1..MAX_LEVEL) — it is NOT a
 * simulation of age, melt, or weathering. That remains explicitly out of
 * scope (Batch 2+). It exists only so lighter/heavier coverage of the same
 * overlay type reads as visually distinct, matching the qualitative
 * examples in the task brief (fresh vs. old snow, thin vs. heavy frost).
 */
public final class OverlayVisualRegistry {

    private OverlayVisualRegistry() {}

    // Base tint at minimum visible level (level = 1).
    private static final RenderColor SNOW_LOW    = new RenderColor(1.00f, 1.00f, 1.00f);
    private static final RenderColor SNOW_HIGH   = new RenderColor(0.80f, 0.82f, 0.86f);

    private static final RenderColor FROST_LOW   = new RenderColor(0.90f, 0.95f, 1.00f);
    private static final RenderColor FROST_HIGH  = new RenderColor(0.78f, 0.88f, 0.98f);

    private static final RenderColor WET_LOW     = new RenderColor(0.55f, 0.55f, 0.55f);
    private static final RenderColor WET_HIGH    = new RenderColor(0.30f, 0.30f, 0.32f);

    private static final RenderColor AUTUMN_LOW  = new RenderColor(0.85f, 0.55f, 0.25f);
    private static final RenderColor AUTUMN_HIGH = new RenderColor(0.65f, 0.35f, 0.15f);

    private static final RenderColor DUST_LOW    = new RenderColor(0.82f, 0.72f, 0.50f);
    private static final RenderColor DUST_HIGH   = new RenderColor(0.70f, 0.58f, 0.38f);

    private static final RenderColor POLLEN_LOW  = new RenderColor(0.95f, 0.90f, 0.35f);
    private static final RenderColor POLLEN_HIGH = new RenderColor(0.85f, 0.78f, 0.25f);

    private static final float OPACITY_LOW  = 0.35f;
    private static final float OPACITY_HIGH = 0.85f;

    public static OverlayVisualProfile resolve(OverlayType type, int level) {
        float t = FogMath.clamp((level - 1f) / (float) (OverlayLevelResolver.MAX_LEVEL - 1), 0f, 1f);

        RenderColor low = lowColorFor(type);
        RenderColor high = highColorFor(type);

        RenderColor tint = new RenderColor(
                FogMath.lerp(low.red(),   high.red(),   t),
                FogMath.lerp(low.green(), high.green(), t),
                FogMath.lerp(low.blue(),  high.blue(),  t)
        );

        float opacity = FogMath.lerp(OPACITY_LOW, OPACITY_HIGH, t);
        float brightness = 1.0f;
        float contrast = 1.0f;

        return new OverlayVisualProfile(tint, opacity, brightness, contrast, 1.0f, 1.0f);
    }

    private static RenderColor lowColorFor(OverlayType type) {
        return switch (type) {
            case SNOW -> SNOW_LOW;
            case FROST -> FROST_LOW;
            case WET -> WET_LOW;
            case AUTUMN -> AUTUMN_LOW;
            case DUST -> DUST_LOW;
            case POLLEN -> POLLEN_LOW;
        };
    }

    private static RenderColor highColorFor(OverlayType type) {
        return switch (type) {
            case SNOW -> SNOW_HIGH;
            case FROST -> FROST_HIGH;
            case WET -> WET_HIGH;
            case AUTUMN -> AUTUMN_HIGH;
            case DUST -> DUST_HIGH;
            case POLLEN -> POLLEN_HIGH;
        };
    }
}