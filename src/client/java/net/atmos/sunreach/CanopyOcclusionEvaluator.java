package net.atmos.sunreach;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cellgrid.CanopyProfile;

/**
 * Canopy Occlusion evaluator — Chapter 8 §13, Stage Four.
 *
 * Chapter 8 §13 specifies no transfer function — only qualitative anchor
 * values (sparse forest ≈0.82, dark forest ≈0.28, jungle ≈0.18). The
 * exponential proximity weighting used here (exp(-distance/PROXIMITY_SCALE))
 * is therefore implementation-defined, justified by consistency with
 * existing Atmos idioms — DepthFogModifier.densityCurve() and
 * AtmosphereDrifter's velocity damping both already use exponential
 * saturating curves — not by any claim of real-world optical accuracy.
 *
 * PROXIMITY_SCALE is chosen jointly with CanopyProfile.SEARCH_HEIGHT_BLOCKS
 * to maintain a ratio of 5.0 (five e-folding lengths); see CanopyProfile's
 * class doc for the canonical explanation of that relationship. This
 * evaluator depends on the Cell-Grid-owned SEARCH_HEIGHT_BLOCKS constant —
 * the correct ownership direction, matching SunReachEvaluator's existing
 * dependency on HorizonMap.SECTOR_COUNT. CanopyProfile never depends on
 * anything defined here.
 *
 * Distance-per-sample is reconstructed via
 * CanopyProfile.sampleFraction(v) * SEARCH_HEIGHT_BLOCKS — the same
 * schedule the generator used to place the sample, so the two can never
 * disagree. Each hit contributes a uniform nominal slab thickness
 * (SEARCH_HEIGHT_BLOCKS / VERTICAL_SAMPLE_COUNT) regardless of where it was
 * placed — placement bias improves detection resolution in the near field;
 * it does not attempt to reconstruct variable physical layer thickness,
 * which would add complexity beyond what Chapter 8 requires. Documented
 * simplification, not a hidden one.
 *
 * Cave-under-canopy safety: CanopyProfileGenerator samples points directly
 * without line-of-sight checking, so a deep cave cell can in principle
 * register a spurious canopy hit from foliage above intervening solid
 * rock (see that class's documented limitation). This evaluator does not
 * correct for that, and does not need to: Chapter 8 §17–18 defines stage
 * combination as straight multiplication specifically so that one stage's
 * blind spot is corrected by another stage that already owns that
 * condition. Stage Two (Terrain Exposure, already implemented) drives its
 * own factor toward zero for that same cave cell via HorizonMap's
 * blocking-elevation angle. Once a future combination step multiplies both
 * factors together, a spurious Stage Four value is nullified by Stage
 * Two's correctly-near-zero value. No ray marching was added here — the
 * architecture's own multi-stage design already makes this safe.
 *
 * Binary per-sample data is not a simplification here either — see
 * CanopyProfile's class doc. The continuous occlusion curve produced here
 * comes entirely from combining many binary samples with distance
 * weighting and the exponential transfer function below.
 *
 * Task boundary: Stage Four only. Not combined with SunReachResult or
 * SkyVisibilityResult, not cached, not wired into any renderer or AtmosCell
 * field. Does not import CellGrid, ClientLevel, or any world type.
 */
public final class CanopyOcclusionEvaluator {

    private CanopyOcclusionEvaluator() {}

    /**
     * Spatial decay constant for proximity weighting. Implementation-defined
     * physical model parameter. Chosen jointly with
     * CanopyProfile.SEARCH_HEIGHT_BLOCKS to maintain a 5.0 e-folding margin
     * — see that class's doc for the canonical explanation.
     */
    private static final float PROXIMITY_SCALE = 5.0f;

    /** Saturation scale for the openness transfer curve. Independent tuning constant. */
    private static final float CANOPY_DEPTH_SCALE = 6.0f;

    /** Soft floor preventing a hard-zero collapse, per Chapter 8 §30. */
    private static final float CANOPY_OPENNESS_FLOOR = 0.12f;

    public static CanopyOcclusionResult evaluate(CanopyProfile canopyProfile) {
        boolean[][] hits = canopyProfile.hitsView();

        float slabThickness = CanopyProfile.SEARCH_HEIGHT_BLOCKS / CanopyProfile.VERTICAL_SAMPLE_COUNT;

        float thicknessSum = 0f;
        float occlusionSum = 0f;

        for (boolean[] column : hits) {
            float weightedThickness = 0f;

            for (int v = 0; v < column.length; v++) {
                if (!column[v]) continue;

                float distance = CanopyProfile.sampleFraction(v) * CanopyProfile.SEARCH_HEIGHT_BLOCKS;
                float weight = (float) Math.exp(-distance / PROXIMITY_SCALE);

                weightedThickness += slabThickness * weight;
            }

            thicknessSum += weightedThickness;
            occlusionSum += 1f - (float) Math.exp(-weightedThickness / CANOPY_DEPTH_SCALE);
        }

        float averageEffectiveThickness = thicknessSum / hits.length;
        float averageOcclusion          = occlusionSum / hits.length;

        float value = FogMath.clamp(1f - averageOcclusion, CANOPY_OPENNESS_FLOOR, 1f);

        return new CanopyOcclusionResult(averageEffectiveThickness, value);
    }
}