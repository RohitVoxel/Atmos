package net.atmos.exposure;

import net.atmos.atmosphere.fog.FogDrifter;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Exposure Model — Chapter 14 Stage 2 (Core Exposure Evaluation).
 *
 * Extends Stage 1's foundation exactly as that stage's own doc
 * anticipated: the plain currentExposureScale float is replaced by a
 * FogDrifter, chosen because §14.7 requires asymmetric bright/dark
 * adaptation and FogDrifter already provides asymmetric build/clear
 * response rates — no new smoothing primitive was invented.
 *
 * update() performs §14.20's Steps 1-5 (Ingest, Evaluate, Calculate,
 * Adapt, Publish) in one call, matching the self-contained lifecycle
 * pattern already established by PerceptualEvaluationSystem.evaluate().
 * publish() remains independently callable, preserving Stage 1's API.
 *
 * Simulation Thread only (§14.12) — not thread-safe, matching CellGrid's
 * identical Appendix D §11 disclaimer.
 */
public final class ExposureModel {

    private final FogDrifter exposureDrifter = new FogDrifter(
            ExposureWeights.EXPOSURE_BASELINE,
            ExposureWeights.DARK_ADAPTATION_SPEED,
            ExposureWeights.BRIGHT_ADAPTATION_SPEED
    );

    private boolean initialized = false;

    private EnvironmentalLuminanceResult lastLuminance =
            new EnvironmentalLuminanceResult(1f, 1f, 1f, 1f);
    private TargetExposureResult lastTarget =
            new TargetExposureResult(ExposureWeights.EXPOSURE_BASELINE, 0f, 0f,
                    ExposureWeights.EXPOSURE_BASELINE);

    /**
     * Evaluates luminance, target exposure, and advances adaptation for
     * the current simulation update, then publishes the result.
     * First call snaps directly to target (Chapter 5-style startup-spike
     * avoidance, matching EnvironmentalState.snapToTargets precedent).
     */
    public void update(ExposureInputs inputs, float deltaSec) {
        EnvironmentalLuminanceResult luminance = EnvironmentalLuminanceEvaluator.evaluate(
                inputs.env(), inputs.cellGrid(), inputs.composition());

        TargetExposureResult target = TargetExposureEvaluator.evaluate(luminance, inputs.memory());

        float safeDelta = FogMath.clamp(deltaSec, 0f, 0.1f);

        if (!initialized) {
            exposureDrifter.snap(target.value());
            initialized = true;
        } else {
            exposureDrifter.advance(target.value(), safeDelta);
        }

        lastLuminance = luminance;
        lastTarget = target;

        publish();
    }

    /** Publishes the current internal state as an immutable snapshot. */
    public void publish() {
        ExposureStateManager.publish(exposureDrifter.get());
    }

    public void reset() {
        exposureDrifter.snap(ExposureWeights.EXPOSURE_BASELINE);
        initialized = false;
        lastLuminance = new EnvironmentalLuminanceResult(1f, 1f, 1f, 1f);
        lastTarget = new TargetExposureResult(ExposureWeights.EXPOSURE_BASELINE, 0f, 0f,
                ExposureWeights.EXPOSURE_BASELINE);
    }

    public float currentExposureScale() { return exposureDrifter.get(); }

    public EnvironmentalLuminanceResult lastLuminance() { return lastLuminance; }

    public TargetExposureResult lastTarget() { return lastTarget; }
}