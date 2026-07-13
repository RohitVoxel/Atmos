package net.atmos.pes;

import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.composition.Composition;

/**
 * Biome Identity evaluator — Chapter 12 §12.12.
 *
 * Compares composition density against the biome's static identity
 * (BiomeTraits.humidity()), not the dynamic simulated value — see
 * EnvironmentalConsistencyEvaluator for that check.
 *
 * §12.12's lighting/contrast/visibility qualities are not evaluated — no
 * such signal exists below RenderCluster (Chapter 9, not produced at this
 * pipeline stage). Only the humidity-driven density identity is checked,
 * matching BiomeModifierEvaluator's precedent (Appendix J) of using
 * humidity alone with the omission documented rather than approximated.
 */
public final class BiomeIdentityEvaluator {

    private BiomeIdentityEvaluator() {}

    public static BiomeIdentityResult evaluate(BiomeTraits traits, Composition composition) {
        float expectedDensity = traits.humidity();
        float densitySignal   = PESMath.compositionDensitySignal(composition);

        float deviation = Math.abs(densitySignal - expectedDensity);
        float value = PESMath.deviationScore(densitySignal, expectedDensity,
                PESWeights.BIOME_IDENTITY_TOLERANCE);

        return new BiomeIdentityResult(deviation, value, PESMath.passesCategoryThreshold(value));
    }
}