package net.atmos.confidence;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.core.CameraSnapshot;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tier C — Geometric Presentation (Chapter 4, Part 1 §Tier C, as corrected
 * by Appendix B §3).
 *
 * Answers: "Is this location worth presenting to the camera right now?"
 * Reads only CameraSnapshot geometry and the world-space point being
 * evaluated — no EnvironmentalState, no Cell Grid.
 *
 * --- Scope correction applied (Appendix B §3) ---
 *
 * The original Chapter 4 text lists "Hero composition" and "Performance
 * budget" among Tier C's inputs. Appendix B §3 explicitly overrides this:
 * those values are computed by systems that run AFTER Confidence (the
 * Composition Engine and APS/ALSC), which would create a circular
 * dependency. Appendix B §3 redefines Tier C strictly as camera-relative
 * geometric facts: distance, camera angle/frustum bounds, screen position,
 * and local exposure scale. This evaluator follows that corrected
 * definition exactly. "Local exposure scale" is omitted — the Exposure
 * Model (Chapter 14) does not exist yet.
 *
 * --- Continuity note ---
 *
 * Both the alignment check (is the target roughly in front of the camera)
 * and the frustum check are naturally binary-ish operations. Per Chapter 4
 * §2's rejection of binary logic, both are soft-floored rather than
 * allowed to hit a literal 0.0 — see ConfidenceWeights.TIER_C_ALIGNMENT_FLOOR
 * / TIER_C_FRUSTUM_FLOOR.
 *
 * --- Known limitation: no temporal smoothing ---
 *
 * Chapter 4 §7-§8 describes confidence as inherently temporal ("Confidence
 * Never Jumps... Confidence Is Always Temporal"), achieved via the
 * Atmospheric Transition State (Chapter 5) and Confidence Memory
 * (Chapter 4 §11-13). Neither Chapter 5 nor Confidence Memory is built yet.
 * This means a target crossing the frustum boundary or swinging past the
 * alignment threshold WILL produce a frame-to-frame confidence step,
 * softened only by the floors above, not eliminated. This is a real,
 * documented gap, not a silent omission — revisit once Chapter 5 exists.
 *
 * --- IMPORTANT: MAX_PRESENTABLE_DISTANCE is a temporary architectural
 *     default, flagged for future relocation ---
 *
 * MAX_PRESENTABLE_DISTANCE below (192 blocks) is currently a hardcoded
 * approximation of a 12-chunk render distance. This value is deliberately
 * NOT centralized into ConfidenceWeights alongside the tier weights
 * (Confidence System Final Cleanup, Task 1) — it is not a "Confidence
 * weight," and moving it there would misrepresent its true architectural
 * home.
 *
 * The correct long-term source for this value is renderer configuration
 * or quality settings (e.g. the player's actual configured render
 * distance, or a future APS/ALSC-driven quality tier), NOT the Confidence
 * System. Tier C must never become the permanent owner of render-distance
 * tuning — it currently holds this value only because no renderer
 * configuration channel reaches this evaluator yet. When that channel
 * exists, MAX_PRESENTABLE_DISTANCE must be removed from this class
 * entirely and replaced with an externally-supplied value.
 *
 * This is a documentation-only note (Task 4). The value itself (192f) is
 * unchanged, and no behavior has changed in this pass.
 */
public final class TierCEvaluator {

    private TierCEvaluator() {}

    // Maximum distance, in blocks, at which a target is still considered
    // geometrically presentable. Approximates a 12-chunk render distance
    // (192 blocks). See this class's doc comment above — this is a
    // temporary default, not a permanent Tier C responsibility.
    private static final float MAX_PRESENTABLE_DISTANCE = 192f;

    // Half-extent of the synthetic probe box used for the frustum test.
    // Tier C evaluates a single world-space point; Frustum#isVisible
    // requires an AABB, so a small box is built around the point purely to
    // satisfy that API shape. It carries no meaning beyond "is this point
    // roughly on screen" and is not a tuning value in the same sense as
    // the Confidence weights — kept local to this class.
    private static final float FRUSTUM_PROBE_HALF_EXTENT = 0.5f;

    public static TierCResult evaluate(CameraSnapshot camera, Vec3 targetWorldPos) {
        Vec3 toTarget = targetWorldPos.subtract(camera.position());
        double distance = toTarget.length();

        float distanceFactor = FogMath.clamp(
                1f - (float) (distance / MAX_PRESENTABLE_DISTANCE), 0f, 1f);

        float alignmentFactor;
        if (distance < 1e-4) {
            // Degenerate case: target coincides with the camera position.
            // Treat as maximally aligned rather than normalizing a
            // near-zero-length vector.
            alignmentFactor = 1.0f;
        } else {
            Vec3 toTargetNorm = toTarget.scale(1.0 / distance);
            double dot = camera.lookDirection().normalize().dot(toTargetNorm);
            float raw = (float) ((dot + 1.0) * 0.5); // [-1,1] -> [0,1]
            alignmentFactor = Math.max(ConfidenceWeights.TIER_C_ALIGNMENT_FLOOR, FogMath.clamp(raw, 0f, 1f));
        }

        AABB probe = new AABB(
                targetWorldPos.x - FRUSTUM_PROBE_HALF_EXTENT,
                targetWorldPos.y - FRUSTUM_PROBE_HALF_EXTENT,
                targetWorldPos.z - FRUSTUM_PROBE_HALF_EXTENT,
                targetWorldPos.x + FRUSTUM_PROBE_HALF_EXTENT,
                targetWorldPos.y + FRUSTUM_PROBE_HALF_EXTENT,
                targetWorldPos.z + FRUSTUM_PROBE_HALF_EXTENT
        );
        Frustum frustum = camera.frustum();
        boolean inFrustum = (frustum != null) && frustum.isVisible(probe);
        float frustumFactor = inFrustum ? 1.0f : ConfidenceWeights.TIER_C_FRUSTUM_FLOOR;

        // Allocation-free three-factor overload (Confidence System Final
        // Cleanup, Task 2) — no float[] allocated per evaluation.
        float value = ConfidenceMath.weightedGeometricProduct(
                distanceFactor,  ConfidenceWeights.TIER_C_WEIGHT_DISTANCE,
                alignmentFactor, ConfidenceWeights.TIER_C_WEIGHT_ALIGNMENT,
                frustumFactor,   ConfidenceWeights.TIER_C_WEIGHT_FRUSTUM
        );

        return new TierCResult(distanceFactor, alignmentFactor, frustumFactor, value);
    }
}