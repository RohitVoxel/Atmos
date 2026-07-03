package net.atmos.sunreach;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cellgrid.HorizonMap;

/**
 * Sky Visibility evaluator — Chapter 8, Stage Three.
 *
 * Per Chapter 8 §12: "Sky visibility measures how much of the sky
 * hemisphere is visible. Unlike terrain, this focuses on openness above
 * the player" — an omnidirectional measure, explicitly distinct from
 * Stage Two's Terrain Exposure (SunReachEvaluator), which evaluates
 * blocking specifically in the sun's current azimuth. This evaluator
 * intentionally ignores solar direction entirely — it answers "how open
 * is the sky here," not "can the sun currently reach here."
 *
 * --- Data source ---
 *
 * Reuses HorizonMap (Chapter 6, Cell Grid-owned) — the same terrain data
 * source SunReachEvaluator already reads — rather than sampling terrain
 * independently. This satisfies "must not bypass the Cell Grid or Horizon
 * Map architecture" and "must not introduce duplicate terrain-analysis
 * systems": no new terrain query is performed anywhere in this class.
 *
 * --- Why this does not reuse TierBEvaluator (Confidence System) ---
 *
 * TierBEvaluator (net.atmos.confidence) already computes a similarly-named
 * omnidirectional average from HorizonMap for a different purpose (Tier
 * B's "Local Opportunity" factor). That method is deliberately not reused
 * here, for three reasons documented at the call site of this decision:
 *
 *   1. Ownership: Appendix C's Data Ownership Table lists Sun Reach and
 *      Confidence as separately-owned data. TierBEvaluator.averageOpenness()
 *      is a private implementation detail of the Confidence System
 *      (Chapter 4), not a shared architectural resource.
 *
 *   2. Documented dependency direction: TierBEvaluator's own class doc
 *      states its uniform-averaging approach is a temporary stand-in that
 *      "MUST be revised... once Sun Reach exists" to consume Sun Reach's
 *      directional output instead. Confidence is documented to eventually
 *      depend on SunReach — reaching the opposite direction (SunReach
 *      depending on a Confidence-internal helper) would invert that
 *      documented relationship.
 *
 *   3. Scope: modifying TierBEvaluator to expose that method, or extracting
 *      a shared utility, requires touching Confidence System files, which
 *      this task's scope explicitly excludes ("Do not modify unrelated
 *      systems").
 *
 * The elevation-to-openness linear conversion below is therefore
 * necessarily re-expressed here (see sectorOpenness()) rather than shared.
 * This is a small (~1-line), well-isolated geometric primitive, not a
 * duplicated system — the actual shared resource (HorizonMap, the terrain
 * data itself) is correctly reused, not duplicated.
 *
 * --- Formula (implementation-defined, per Chapter 8 §12's own lack of an
 *     exact formula — same status as Appendix F §3.1's azimuth mapping) ---
 *
 * Chapter 8 §12 gives only qualitative anchor examples (open sky ≈1.00,
 * forest ≈0.45, deep ravine ≈0.30) and no exact formula. This evaluator
 * uses an unweighted arithmetic mean of per-sector openness across all
 * HorizonMap.SECTOR_COUNT sectors:
 *
 *     openness(sector) = clamp((π/2 − blockingElevationAngle) / π, 0, 1)
 *     skyVisibility     = mean(openness(sector) for all sectors)
 *
 * This is the same per-sector conversion TierBEvaluator independently
 * arrived at for the same physical quantity (elevation angle range
 * [-π/2, +π/2] representing "fully open" to "terrain directly overhead"),
 * chosen here for consistency with that precedent rather than inventing a
 * second, different mapping. No solid-angle weighting is applied — Chapter
 * 8 does not specify one, and none exists anywhere else in the codebase;
 * adding one here would be a speculative, undocumented refinement.
 *
 * --- Ownership, thread model, lifecycle ---
 *
 * Stateless and deterministic, matching SunReachEvaluator's pattern
 * exactly: evaluate() is a pure function of a HorizonMap. No caching, no
 * temporal smoothing, no per-cell state — out of scope for this task
 * (Cell Grid ownership, a later integration step). Per Appendix D §11,
 * intended for Simulation Thread use only, consistent with every other
 * SunReach/Confidence evaluator.
 *
 * --- Task boundary ---
 *
 * This class evaluates Chapter 8 Stage Three only. Canopy Occlusion,
 * Weather Attenuation, Humidity Response, Biome Illumination Modifier,
 * temporal smoothing, caching, and combination with SunReachEvaluator's
 * Stage One/Two output are all out of scope and not implemented here.
 * Nothing in AtmosClient, the renderer, or SunReachEvaluator/SunReachResult
 * was touched.
 */
public final class SkyVisibilityEvaluator {

    private SkyVisibilityEvaluator() {}

    private static final float HALF_PI = (float) (Math.PI / 2.0);

    public static SkyVisibilityResult evaluate(HorizonMap horizonMap) {
        // sectorsView() returns a defensive copy per HorizonMap's read-only
        // access contract (Appendix F §3) — same read pattern already used
        // by SunReachEvaluator and TierBEvaluator.
        float[] sectors = horizonMap.sectorsView();

        float sum = 0f;
        for (float blockingElevationAngle : sectors) {
            sum += sectorOpenness(blockingElevationAngle);
        }

        float value = FogMath.clamp(sum / sectors.length, 0f, 1f);
        return new SkyVisibilityResult(value);
    }

    /**
     * Converts one sector's blocking elevation angle into an openness
     * fraction: -π/2 (terrain fully absent, open to the horizon) → 1.0,
     * +π/2 (terrain directly overhead) → 0.0.
     */
    private static float sectorOpenness(float blockingElevationAngle) {
        return FogMath.clamp((HALF_PI - blockingElevationAngle) / (float) Math.PI, 0f, 1f);
    }
}