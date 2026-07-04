package net.atmos.sunreach;

/**
 * Explainable breakdown of one Canopy Occlusion evaluation (Chapter 8 §13,
 * Stage Four).
 *
 * averageEffectiveThickness: mean proximity-weighted effective foliage
 * thickness across probe columns, in blocks, reconstructed using
 * CanopyProfile's shared sampleFraction() placement schedule. See
 * CanopyOcclusionEvaluator's class doc for the full audited reasoning
 * behind this value's derivation.
 */
public record CanopyOcclusionResult(
        float averageEffectiveThickness,
        float value
) {}