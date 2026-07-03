package net.atmos.sunreach;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cellgrid.HorizonMap;

/**
 * SunReach evaluator — Chapter 8, Stages One and Two (Solar Position,
 * Terrain Exposure), implementing the runtime evaluation contract defined
 * in Appendix F §3 and the solar-direction model defined in Appendix F §3.1
 * (Solar Direction Computational Contract).
 *
 * --- Revision history ---
 *
 * Two prior AUTOPSY findings applied against this evaluator:
 *
 *   1. The original implementation derived azimuth via atan2(0f, -sinAngle),
 *      which — because the first argument is permanently 0f — is a step
 *      function of sign(-sinAngle), collapsing to exactly two possible
 *      azimuths and switching discretely at solar noon/midnight.
 *
 *   2. The first fix evaluated both of those two fixed azimuths every call
 *      and blended them continuously by sinAngle. This removed the
 *      discontinuity but still violated Appendix F §3.1: it evaluated two
 *      permanent directional states rather than one continuously changing
 *      current sun direction, and it only ever sampled 2 of the Horizon
 *      Map's 8 sectors — Appendix F §3.1 requires the azimuth mapping to
 *      "span the complete directional domain represented by the Horizon
 *      Map."
 *
 * Both findings shared one root cause: they derived azimuth from
 * CrepuscularRayRenderer's documented sun-direction vector
 * (−sinAngle, sunHeight, 0), which is permanently confined to world Z=0 by
 * design (a rendering-layer simplification, not a simulation-layer
 * definition). Appendix F §3.1 explicitly prohibits this reuse: "the
 * simplified sun-direction convention used by rendering systems... shall
 * never be reused as the authoritative simulation definition of solar
 * direction."
 *
 * --- Current fix ---
 *
 * Per Appendix F §3.1, the celestial-angle-to-azimuth mapping is
 * implementation-defined provided it is deterministic, continuous, produces
 * identical azimuths for identical angles, evolves continuously through the
 * full day-night cycle, introduces no runtime discontinuities, and spans
 * the Horizon Map's complete directional domain.
 *
 * This evaluator now uses sunAngleRadians directly as the Horizon Map
 * azimuth. sunAngleRadians is Minecraft's own celestial rotation angle,
 * already sampled once per frame via FogContext.sunAngle() — it advances
 * continuously and monotonically through a full 2π sweep once per day-night
 * cycle with no branch, sign check, or selection logic applied to it. This
 * satisfies all six properties in Appendix F §3.1 directly: see the
 * revision analysis in the accompanying explanation for the full check
 * against each property.
 *
 * There is exactly one azimuth per evaluation, computed and interpolated
 * once. No second direction is computed. No blending between directional
 * states occurs.
 *
 * --- What this evaluator computes ---
 *
 * Solar Position (Chapter 8 §10):
 *   Unchanged from the original implementation —
 *       sunHeight = cos(sunAngleRadians)
 *       solarPositionFactor = clamp(sunHeight, 0, 1)
 *   matching the sun-height convention already established throughout the
 *   codebase (EnvironmentalState, DaylightFogModifier, SkyColorController).
 *
 * Terrain Exposure (Chapter 8 §11, Appendix F §3, Appendix F §3.1):
 *   The single continuous azimuth above is interpolated between the two
 *   nearest Horizon Map sectors (Appendix F §3's "Interpolation" step),
 *   producing one blocking-elevation angle. That angle is compared against
 *   the sun's current elevation using the same continuous transition band
 *   (TERRAIN_TRANSITION_MARGIN) already established in the prior revision,
 *   so a terrain silhouette crossing still rises/falls smoothly rather than
 *   snapping — this part of the architecture was already correct and is
 *   unchanged.
 *
 * Strictly O(1): one sectorsView() clone, one azimuth normalization, two
 * array reads, two lerps (sector interpolation, transition smoothing), no
 * ray marching, no block sampling, no world query — per Appendix F §3.
 *
 * --- Combination rule ---
 *
 * Chapter 8 §17–§18: straight multiplication of the two stage factors, not
 * the Confidence System's weighted geometric product (Appendix D §7).
 * Unchanged from the original implementation.
 *
 * --- Task boundary ---
 *
 * Unchanged: Sky Visibility, Canopy, Weather, Humidity, Biome Modifier
 * stages, temporal smoothing, per-cell caching, and all downstream wiring
 * remain out of scope for this task.
 */
public final class SunReachEvaluator {

    private SunReachEvaluator() {}

    private static final float TERRAIN_TRANSITION_MARGIN = 0.05f;

    private static final float TWO_PI       = (float) (2.0 * Math.PI);
    private static final float SECTOR_ANGLE = TWO_PI / HorizonMap.SECTOR_COUNT;

    public static SunReachResult evaluate(HorizonMap horizonMap, float sunAngleRadians) {
        float sunHeight = (float) Math.cos(sunAngleRadians);
        float solarPositionFactor = FogMath.clamp(sunHeight, 0f, 1f);

        float sunElevation = (float) Math.asin(FogMath.clamp(sunHeight, -1f, 1f));

        // Single continuous solar azimuth, per Appendix F §3.1. sunAngleRadians
        // is used directly — deterministic, continuous, spans the full 2π
        // Horizon Map domain, no branching, no second direction computed.
        float[] sectors = horizonMap.sectorsView();
        float blockingAngle = interpolatedBlockingAngle(sectors, sunAngleRadians);

        float delta = sunElevation - blockingAngle;
        float t = FogMath.clamp((delta / TERRAIN_TRANSITION_MARGIN + 1f) * 0.5f, 0f, 1f);
        float terrainVisibilityFactor = FogMath.smoothstep(t);

        // Chapter 8 §17–§18: straight multiplication — unchanged.
        float value = FogMath.clamp(solarPositionFactor * terrainVisibilityFactor, 0f, 1f);

        return new SunReachResult(solarPositionFactor, terrainVisibilityFactor, value);
    }

    /**
     * Linearly interpolates between the two Horizon Map sectors nearest
     * {@code azimuthRadians}, per Appendix F §3's "Interpolation" step.
     */
    private static float interpolatedBlockingAngle(float[] sectors, float azimuthRadians) {
        float normalized = azimuthRadians % TWO_PI;
        if (normalized < 0f) normalized += TWO_PI;

        float rawIndex   = normalized / SECTOR_ANGLE;
        int   lowerIndex = (int) rawIndex % HorizonMap.SECTOR_COUNT;
        int   upperIndex = (lowerIndex + 1) % HorizonMap.SECTOR_COUNT;
        float frac        = rawIndex - (float) Math.floor(rawIndex);

        return FogMath.lerp(sectors[lowerIndex], sectors[upperIndex], frac);
    }
}