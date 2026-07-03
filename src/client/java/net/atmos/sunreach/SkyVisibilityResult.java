package net.atmos.sunreach;

/**
 * Result of one Sky Visibility evaluation (Chapter 8, Stage Three).
 *
 * Unlike SunReachResult (Stages One-Two), Sky Visibility is not a product
 * of multiple named sub-factors — Chapter 8 §12 describes it as a single
 * omnidirectional measure ("how much of the sky hemisphere is visible"),
 * not a combination. A single field is therefore the honest representation
 * of what this stage produces; no additional breakdown fields are invented
 * here purely for symmetry with SunReachResult.
 */
public record SkyVisibilityResult(
        float value
) {}