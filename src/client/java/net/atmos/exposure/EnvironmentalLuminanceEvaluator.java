package net.atmos.exposure;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;
import net.atmos.composition.Composition;

import java.util.Collection;

/**
 * Environmental Luminance evaluator — Chapter 14 §14.6.
 *
 * Fuses global illumination (EnvironmentalState), local cell openness
 * (CellGrid), and visual salience (Composition) into one continuous
 * luminance estimate in [0,1]. Purely mathematical — no GPU/screen-space
 * reads, no world queries (CellGrid's skyExposed flag is already cached
 * per-cell, not sampled here).
 *
 * Directional lighting (SunReach) is deliberately omitted: no per-frame
 * SunReach snapshot is threaded into ExposureInputs (see that record's
 * class doc — the identical omission already established for Stage 1).
 * Biome-specific response (§14.8) is likewise omitted — ExposureInputs
 * carries no BiomeTraits; only the weather-driven half of §14.8
 * (storm/humidity attenuation) is representable with current inputs.
 *
 * Stateless, deterministic, O(activeCells) — bounded by CellGrid's own
 * active-radius bound, never proportional to world size.
 */
public final class EnvironmentalLuminanceEvaluator {

    private EnvironmentalLuminanceEvaluator() {}

    public static EnvironmentalLuminanceResult evaluate(EnvironmentalState env,
                                                        CellGrid cellGrid,
                                                        Composition composition) {
        float globalIlluminance = globalIlluminance(env);
        float localOpenness = localOpenness(cellGrid);
        float visualSalienceDamping = visualSalienceDamping(composition);

        float value = FogMath.clamp(
                globalIlluminance * localOpenness * visualSalienceDamping, 0f, 1f);

        return new EnvironmentalLuminanceResult(
                globalIlluminance, localOpenness, visualSalienceDamping, value);
    }

    /** Day presence (1 - nightDepth) attenuated by haze and storm overcast. */
    private static float globalIlluminance(EnvironmentalState env) {
        float dayPresence = 1f - env.getNightDepth();
        float hazeAttenuation = 1f - env.getSkyMoisture() * ExposureWeights.HAZE_DIMMING_STRENGTH;
        float stormAttenuation = 1f - env.getStormEnergy() * ExposureWeights.STORM_DARKENING_STRENGTH;
        return FogMath.clamp(dayPresence * hazeAttenuation * stormAttenuation, 0f, 1f);
    }

    /** Mean sky-exposure fraction across active cells, soft-floored. Neutral if no cells are loaded. */
    private static float localOpenness(CellGrid cellGrid) {
        Collection<AtmosCell> activeCells = cellGrid.getActiveCells();
        if (activeCells.isEmpty()) {
            return ExposureWeights.LOCAL_OPENNESS_NEUTRAL;
        }

        int exposedCount = 0;
        for (AtmosCell cell : activeCells) {
            if (cell.skyExposed()) exposedCount++;
        }
        float fraction = (float) exposedCount / activeCells.size();
        return FogMath.lerp(ExposureWeights.LOCAL_OPENNESS_FLOOR, 1f, fraction);
    }

    /** §14.10 — a strong Hero Cluster subtly reduces the luminance estimate to preserve its definition. */
    private static float visualSalienceDamping(Composition composition) {
        if (composition.heroCluster() == null) return 1f;
        float intensity = FogMath.clamp(composition.heroCluster().averageAtmosphericValue(), 0f, 1f);
        return 1f - intensity * ExposureWeights.VISUAL_SALIENCE_DAMPING_MAX;
    }
}