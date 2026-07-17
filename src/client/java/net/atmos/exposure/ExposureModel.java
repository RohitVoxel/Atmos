package net.atmos.exposure;

/**
 * Exposure Model — Chapter 14 Stage 1 (Architectural Foundation).
 *
 * Per §14.4's Ownership Matrix, the Exposure Model exclusively owns its
 * temporal adaptation state and the finalized Exposure Scale. Per §14.3's
 * State Encapsulation Contract, that internal state is never externally
 * observable except through the immutable {@link ExposureStateSnapshot}
 * published via {@link ExposureStateManager}.
 *
 * Executes exclusively on the Simulation Thread (§14.12) — not
 * thread-safe, must not be accessed from the Render Thread, matching
 * {@code CellGrid}'s identical Appendix D §11 disclaimer. Never touches
 * rendering: no GPU reads, no RenderCluster mutation, no geometry
 * generation, no composition or lighting decisions (§14.5).
 *
 * --- Stage 1 boundary ---
 *
 * Establishes ownership and the publish mechanism only.
 * {@code currentExposureScale} is a plain field, not yet wrapped in a
 * smoothing primitive: §14.7 requires asymmetric bright/dark adaptation
 * rates, and choosing between a symmetric {@code AtmosphereDrifter} and
 * an asymmetric {@code FogDrifter}-style primitive is itself part of that
 * unimplemented algorithm. Committing to one now would prejudge Stage 2.
 * The field starts at, and {@link #reset()} restores,
 * {@link ExposureWeights#EXPOSURE_BASELINE} — an honest "no adjustment
 * yet" value, matching the {@code OptimizationPlan} APS-failsafe precedent.
 *
 * No {@code update(ExposureInputs, float)} method exists yet — §14.6
 * (Environmental Luminance), §14.7 (Target Exposure & Adaptation), §14.9
 * (Memory Integration), §14.10 (Composition/Director interaction), and
 * §14.11 (Predictive consumption) remain unimplemented.
 * {@link ExposureInputs} is defined and ready for that future method.
 */
public final class ExposureModel {

    private float currentExposureScale = ExposureWeights.EXPOSURE_BASELINE;

    /** Publishes the current internal state as an immutable snapshot. */
    public void publish() {
        ExposureStateManager.publish(currentExposureScale);
    }

    public void reset() {
        currentExposureScale = ExposureWeights.EXPOSURE_BASELINE;
    }

    /** Current internal exposure scale — Stage 1: always the baseline. */
    public float currentExposureScale() {
        return currentExposureScale;
    }
}