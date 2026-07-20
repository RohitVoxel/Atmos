package net.atmos.seasonal;

/**
 * Internal, unpublished continuous profile weights — Chapter 15 §15.7,
 * Appendix X §3 (Seasonal Profile Model), Stage 4.
 *
 * Represents the four descriptive seasonal profiles (Warm, Cold, Wet, Dry)
 * as continuous, non-mutually-exclusive intensities in [0,1], derived from
 * a single pair of independent periodic axes (thermalAxis, moistureAxis)
 * per §15.10's "Biome Independence" framing — warmth+coldness=1 and
 * wetness+dryness=1 at every seasonalProgress, so the four profiles blend
 * fluidly with no discrete switching.
 *
 * Package-private by design: per Appendix X §3 these profiles "act purely
 * as biases intended to influence downstream atmospheric density and
 * state behavior" and must never be published via
 * {@link SeasonalFeelingSnapshot} or {@link SeasonalFeelingStateManager}.
 * This is consumed only by {@link ContinuousBiasGenerator} (Stage 3).
 */
record SeasonalProfileWeights(
        float warmth,
        float coldness,
        float wetness,
        float dryness,
        float thermalAxis,
        float moistureAxis
) {}