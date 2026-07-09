package net.atmos.composition;

/**
 * Explainable breakdown of one Hero Score evaluation (Chapter 10 Part 3).
 *
 * Chapter 10 Part 3 defines Hero Score as the product of Confidence, Travel
 * Alignment, Depth Quality, Uniqueness, Sun Reach, and Temporal Stability.
 * See {@link CompositionWeights} for why Travel Alignment, Sun Reach, and
 * Temporal Stability are omitted from this Stage 2 implementation.
 */
public record HeroScoreResult(
        float confidenceFactor,
        float depthQualityFactor,
        float uniquenessFactor,
        float value
) {}