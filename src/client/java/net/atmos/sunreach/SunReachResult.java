package net.atmos.sunreach;

/**
 * Explainable breakdown of one SunReach evaluation (Chapter 8, Stages One
 * and Two — Solar Position and Terrain Exposure).
 *
 * Mirrors the TierAResult / TierBResult / TierCResult pattern established
 * by the Confidence System (Chapter 4): every value that fed the final
 * scalar is retained so a future debug overlay (Chapter 8 §31/§40) can
 * render it directly without recomputation.
 *
 * Only this task's two stages are represented. Sky Visibility, Canopy,
 * Weather, Humidity, and Biome Modifier factors (Chapter 8 §12–§16) do not
 * exist yet — adding placeholder fields for them here would violate the
 * "no placeholder logic" rule. Later Chapter 8 tasks will extend this
 * record additively as those stages are implemented.
 */
public record SunReachResult(
        float solarPositionFactor,
        float terrainVisibilityFactor,
        float value
) {}