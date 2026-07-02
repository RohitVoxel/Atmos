package net.atmos.confidence;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.HorizonMap;

/**
 * Tier B — Local Opportunity (Chapter 4, Part 1 §Tier B).
 *
 * Answers: "Even if the atmosphere as a whole supports this phenomenon
 * (Tier A), can THIS specific location actually produce it?" Reads only
 * Cell Grid data — no EnvironmentalState, no camera — per Chapter 4 §5's
 * Tier B input list ("Sky Exposure," "Sun Reach," "Terrain Occlusion,"
 * "Canopy Density," ...).
 *
 * --- Factor selection (deviation note) ---
 *
 * Two factors are used, both sourced from what the Cell Grid actually
 * stores per its approved scope (no SunReach, no Confidence, no
 * Illumination fields exist on AtmosCell):
 *
 *   1. Terrain openness — derived from the cell's HorizonMap. Each of the
 *      8 stored sectors holds a terrain-blocking elevation angle; this is
 *      converted into a continuous [0,1] openness value per sector
 *      (-90° fully open -> 1.0, +90° fully blocked -> 0.0) and averaged.
 *      This is a genuinely continuous signal, consistent with Chapter 4
 *      §2's explicit rejection of binary logic ("Humidity > 0.5 -> Render
 *      Shaft" is called out as the WRONG approach).
 *
 *   2. Sky exposure — AtmosCell.skyExposed() is a Minecraft-primitive
 *      boolean (level.canSeeSky()), which IS binary at its source and
 *      cannot be made continuous without inventing a canopy-density value
 *      that doesn't exist in the codebase (would violate "no placeholder
 *      logic"). To avoid a hard multiplicative zero on every cell under a
 *      single leaf block, a soft floor (ConfidenceWeights.TIER_B_SKY_EXPOSURE_FLOOR)
 *      is applied instead of literal 0.0 — the minimum concession needed to
 *      keep the tier's multiplicative behaviour continuous rather than a
 *      hard cliff.
 *
 * Canopy Density and Sun Reach (also named in Chapter 4 §5's Tier B list)
 * are intentionally NOT implemented: Canopy Density has no data source in
 * the current Cell Grid (heightmap sampling used for HorizonMap explicitly
 * ignores leaves — MOTION_BLOCKING_NO_LEAVES), and Sun Reach is Chapter 8's
 * system, not yet built. Wiring either in now would mean inventing values
 * or reaching into an unbuilt system — both forbidden by scope.
 *
 * --- IMPORTANT: temporary algorithm, flagged for future revision ---
 *
 * The terrain-openness factor currently averages all 8 HorizonMap sectors
 * EQUALLY, regardless of the sun's current position. This is acceptable
 * only as an interim measure, because the Sun Reach System (Chapter 8),
 * which alone knows the current sun azimuth/elevation, does not exist yet.
 * Equal averaging answers "how open is this cell in general," not "how
 * open is this cell toward the sun right now" — those are different
 * questions, and Tier B is currently answering the wrong one out of
 * necessity rather than design intent.
 *
 * Once Sun Reach exists, this evaluator MUST be revised to weight sectors
 * directionally instead of averaging them uniformly. The expected shape of
 * that future change:
 *
 *   Morning
 *   ↓
 *   East-facing sectors should dominate the openness average
 *   (sun is low in the east; east-side terrain/canopy gaps matter most)
 *
 *   Midday
 *   ↓
 *   Sun-facing (near-overhead) sectors should dominate
 *   (sun angle is steep; horizontal terrain occlusion matters less,
 *   overhead canopy/terrain matters more)
 *
 *   Evening
 *   ↓
 *   West-facing sectors should dominate
 *   (mirror of Morning, opposite horizon side)
 *
 *   Night
 *   ↓
 *   No directional weighting is meaningful — sun elevation is negative
 *   and Tier A will already have collapsed toward 0 via thermalEnergy,
 *   so Tier B's directional shape stops mattering at that point anyway.
 *
 * This is a documentation-only note (Confidence System Final Cleanup,
 * Task 3). No algorithm change has been made in this pass — averageOpenness()
 * below is byte-for-byte identical to its prior implementation.
 */
public final class TierBEvaluator {

    private TierBEvaluator() {}

    private static final float HALF_PI = (float) (Math.PI / 2.0);

    public static TierBResult evaluate(AtmosCell cell) {
        float openness    = averageOpenness(cell.horizonMap());
        float skyExposure = cell.skyExposed() ? 1.0f : ConfidenceWeights.TIER_B_SKY_EXPOSURE_FLOOR;

        // Allocation-free two-factor overload (Confidence System Final
        // Cleanup, Task 2) — no float[] allocated per evaluation.
        float value = ConfidenceMath.weightedGeometricProduct(
                openness,    ConfidenceWeights.TIER_B_WEIGHT_TERRAIN_OPENNESS,
                skyExposure, ConfidenceWeights.TIER_B_WEIGHT_SKY_EXPOSURE
        );

        return new TierBResult(openness, skyExposure, value);
    }

    // Uniform (non-directional) average across all sectors. See this
    // class's doc comment — this is the temporary algorithm awaiting
    // directional revision once Sun Reach exists. Unchanged in this pass.
    private static float averageOpenness(HorizonMap horizonMap) {
        // sectorsView() returns a defensive copy per HorizonMap's read-only
        // access contract (Appendix F §3) — safe to iterate without
        // affecting the cell's stored map.
        float[] sectors = horizonMap.sectorsView();
        float sum = 0f;
        for (float angle : sectors) {
            sum += sectorOpenness(angle);
        }
        return sum / sectors.length;
    }

    private static float sectorOpenness(float elevationAngle) {
        // -HALF_PI (fully open sky) -> 1.0
        // +HALF_PI (terrain directly overhead) -> 0.0
        return FogMath.clamp((HALF_PI - elevationAngle) / (float) Math.PI, 0f, 1f);
    }
}