package net.atmos.exposure;

import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogDrifter;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Exposure Model — Chapter 14.
 *
 * Stage 1 shipped only the persistent current-exposure field, deliberately
 * pinned at EXPOSURE_BASELINE, with the extension point documented as
 * "float -> FogDrifter." Stage 2 supplied the target-computation formula
 * (TargetExposureEvaluator) as a standalone, unwired evaluator. This
 * revision performs that wiring — EnvironmentalLuminanceEvaluator ->
 * TargetExposureEvaluator -> FogDrifter — replacing the Stage 1 float
 * field exactly at its documented replacement point, then adds Stage 3's
 * SunReach input and movement-speed adaptation scaling on top.
 *
 * Movement-speed adaptation scaling (§14.9, §14.25): FogDrifter's
 * buildSpeed/clearSpeed are fixed at construction and cannot be scaled
 * per-call, and FogDrifter is a heavily-reused Chapter 3/5 primitive that
 * must not be modified for this. Instead, movement scales the *effective
 * deltaSec* passed to advance() — mathematically equivalent to scaling
 * the drifter's response speed, achieved entirely at this call site
 * without touching frozen code.
 *
 * Teleportation (§14.9 "Immediate Recalculation"): the target is already
 * recomputed from scratch every update() call (no caching of
 * lastTarget across ticks), so environmental jumps are reflected
 * immediately — only the drifter's convergence toward that target remains
 * gradual. reset() additionally snaps the drifter for genuine session/
 * dimension teardown, mirroring FogManager's identical reset() pattern;
 * wiring reset() to an AtmosClient dimension-change hook is a future
 * integration task, not part of this stage (see Deferred Items).
 *
 * Simulation Thread only (§14.12) — not thread-safe, matching CellGrid's
 * identical Appendix D §11 disclaimer.
 */
public final class ExposureModel {

    private final FogDrifter exposureDrifter = new FogDrifter(
            ExposureWeights.EXPOSURE_BASELINE,
            ExposureWeights.EXPOSURE_DRIFTER_BUILD_SPEED,
            ExposureWeights.EXPOSURE_DRIFTER_CLEAR_SPEED);

    private float currentExposureScale = ExposureWeights.EXPOSURE_BASELINE;

    /** Null until the first update() — no fabricated snapshot is substituted. */
    private RawExposureFactors lastFactors = null;
    private EnvironmentalLuminanceResult lastLuminance = null;
    private TargetExposureResult lastTarget = null;

    public void update(ExposureInputs inputs, float deltaSec) {
        lastFactors   = ExposureFactorSampler.sample(inputs.env(), inputs.memory());
        lastLuminance = EnvironmentalLuminanceEvaluator.evaluate(lastFactors, inputs.sunAngleRadians());
        lastTarget    = TargetExposureEvaluator.evaluate(lastLuminance, inputs.memory());

        float scaledDeltaSec = Math.max(0f, deltaSec) * adaptationSpeedScale();
        currentExposureScale = exposureDrifter.advance(lastTarget.value(), scaledDeltaSec);

        publish();
    }

    public void publish() {
        ExposureStateManager.publish(currentExposureScale);
    }

    public void reset() {
        currentExposureScale = ExposureWeights.EXPOSURE_BASELINE;
        exposureDrifter.snap(ExposureWeights.EXPOSURE_BASELINE);
        lastFactors   = null;
        lastLuminance = null;
        lastTarget    = null;
    }

    /**
     * Movement-speed adaptation scaling — §14.9, §14.25. Reuses
     * FogContext.getSmoothedSpeed(), the single authoritative smoothed
     * player-speed estimate already shared by FogInterpolator, rather than
     * sampling player velocity independently. Continuous walk->elytra
     * ramp rather than discrete movement-mode states, matching Chapter 4
     * §2's rejection of binary/stepped logic.
     */
    private float adaptationSpeedScale() {
        float speed = FogContext.getSmoothedSpeed();
        float t = FogMath.clamp(
                (speed - ExposureWeights.ADAPTATION_SPEED_WALK_THRESHOLD)
                        / (ExposureWeights.ADAPTATION_SPEED_FAST_THRESHOLD - ExposureWeights.ADAPTATION_SPEED_WALK_THRESHOLD),
                0f, 1f);
        return FogMath.lerp(ExposureWeights.ADAPTATION_SCALE_WALK, ExposureWeights.ADAPTATION_SCALE_ELYTRA, t);
    }

    public float currentExposureScale()             { return currentExposureScale; }
    public RawExposureFactors lastFactors()          { return lastFactors; }
    public EnvironmentalLuminanceResult lastLuminance() { return lastLuminance; }
    public TargetExposureResult lastTarget()         { return lastTarget; }
}