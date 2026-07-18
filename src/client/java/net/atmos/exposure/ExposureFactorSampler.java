package net.atmos.exposure;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.memory.AtmosphericMemorySnapshot;

/**
 * Raw exposure-factor sampling — Chapter 14 §14.5.
 *
 * Performs no aggregation, weighting, or combination. Every field is a
 * direct read of a value already owned and clamped by its existing
 * upstream owner — no re-clamping here, same precedent as TierAEvaluator.
 *
 * CellGrid is deliberately not sampled. §14.5 does not name Cell Grid as
 * an exposure source at all — it appears only as a pipeline-position
 * predecessor in §14.31/§14.40's diagram. No section specifies which
 * per-cell field represents an exposure source or how per-cell data
 * should reduce to one value; any such reduction (mean, max, count
 * fraction) would be invented. Deferred pending an explicit Guide
 * specification, not approximated.
 *
 * memory is nullable — absent memory yields 0f for both fields
 * independently; this is a null-safety default on each field, not a
 * combination of the two.
 */
public final class ExposureFactorSampler {

    private ExposureFactorSampler() {}

    public static RawExposureFactors sample(EnvironmentalState env, AtmosphericMemorySnapshot memory) {
        float humidityMemory = memory != null ? memory.humidityMemory() : 0f;
        float stormMemory    = memory != null ? memory.stormMemory()    : 0f;

        return new RawExposureFactors(
                env.getNightDepth(),
                env.getThermalEnergy(),
                env.getHumidityMass(),
                env.getSkyMoisture(),
                env.getStormEnergy(),
                humidityMemory,
                stormMemory
        );
    }
}