package net.atmos.director;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.composition.Composition;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Immutable bundle of everything one Atmosphere Director evaluation
 * consumes, per Chapter 11 §11.6 ("Inputs").
 *
 * env         — current {@link EnvironmentalState}.
 * composition — current cycle's {@link Composition}.
 * biome       — current dominant biome, per §11.18's biome-change rule.
 *
 * --- Stage 3 addition: sunAngleRadians ---
 *
 * Chapter 11 §11.22's "GoldenHourBonus" factor (Appendix C §4) requires
 * the current sun angle to evaluate a dawn/dusk horizon weighting. Added
 * additively here, sourced by the caller from the already-existing
 * {@code FogContext.sunAngle()} value (sampled once per frame elsewhere
 * in the pipeline) — matching the identical caller-extracts-primitive
 * idiom already used by {@code WeatherAttenuationEvaluator.evaluate(float, float)}.
 * No new sun-angle sampling system is introduced.
 *
 * Rain History, Altitude, Player Speed, Travel Direction, Recent Hero
 * Moments, Visual Fatigue, Atmospheric Memory, Exposure, Performance
 * Budget, and Recent Atmospheric Quality Score remain out of scope,
 * unchanged from Stage 1/2.
 */
public record DirectorInputs(
        EnvironmentalState env,
        Composition composition,
        Holder<Biome> biome,
        float sunAngleRadians
) {
    public DirectorInputs {
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }
        if (composition == null) {
            throw new IllegalArgumentException("composition must not be null");
        }
        if (biome == null) {
            throw new IllegalArgumentException("biome must not be null");
        }
        if (!Float.isFinite(sunAngleRadians)) {
            throw new IllegalArgumentException(
                    "sunAngleRadians must be finite, got " + sunAngleRadians);
        }
    }
}