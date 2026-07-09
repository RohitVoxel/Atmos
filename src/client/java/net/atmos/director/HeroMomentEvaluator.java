package net.atmos.director;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.confidence.TierAResult;

/**
 * Hero Moment Score evaluator — Chapter 11 §11.21-§11.22, Stage 3.
 *
 * Stateless, deterministic, O(1). See {@link HeroMomentResult} for the
 * documented scope reduction (CompositionScore, ExposureScore, and
 * MemoryBonus omitted — no architectural producer exists for any of them).
 *
 * --- Golden Hour factor derivation ---
 *
 * Chapter 11 gives no numeric anchor for "GoldenHourBonus." This evaluator
 * reuses {@code FogMath.horizonFactor(sunHeight, sinAngle)} — the exact
 * dawn/dusk weighting function already shared by DaylightFogModifier,
 * SkyColorController, and FogMixin — rather than inventing a second
 * golden-hour curve. Hero Moments intentionally share the exact same
 * horizon weighting used elsewhere in the pipeline so that Peak eligibility
 * always aligns with the same dawn/dusk window the player is already seeing
 * expressed in fog and sky color — do not replace this with a simpler
 * {@code cos(angle)}-based approximation; that would decouple Hero Moment
 * timing from the rest of the atmosphere's visual dawn/dusk identity.
 * sunAngleRadians is supplied via {@link DirectorInputs} (extended
 * additively in Stage 3), sourced by the caller from the same
 * {@code FogContext.sunAngle()} value already sampled once per frame
 * elsewhere in the pipeline (Permanent Instructions: "Extend Before
 * Creating" — no new sun-angle sampling is introduced).
 *
 * --- Known consequence, documented rather than hidden (see delivery
 *     report Hidden Assumption Audit) ---
 *
 * stormClearingFactor and goldenHourFactor are both continuous signals
 * that are frequently at or near 0.0 by their own natural definition
 * (storm clearing only has a nonzero value while a storm is actively
 * dissipating; the horizon factor is only nonzero near dawn/dusk). No
 * soft floor is applied to either — unlike Confidence's TierB/TierC
 * floors, which exist specifically because their underlying signal is
 * binary (canSeeSky, frustum containment). These two factors are already
 * continuous, so that floor justification does not transfer, and
 * inventing a floor value with no chapter anchor would itself be an
 * unjustified assumption.
 */
public final class HeroMomentEvaluator {

    private HeroMomentEvaluator() {}

    public static HeroMomentResult evaluate(TierAResult tierA, EnvironmentalState env, float sunAngleRadians) {
        float tierAFactor = tierA.value();

        float stormClearingFactor = env.getStormClearing();

        float sunHeight = (float) Math.cos(sunAngleRadians);
        float sinAngle  = (float) Math.sin(sunAngleRadians);
        float goldenHourFactor = FogMath.horizonFactor(sunHeight, sinAngle);

        float humidityFactor = env.getHumidityMass();

        float value = tierAFactor * stormClearingFactor * goldenHourFactor * humidityFactor;

        return new HeroMomentResult(tierAFactor, stormClearingFactor, goldenHourFactor, humidityFactor, value);
    }
}