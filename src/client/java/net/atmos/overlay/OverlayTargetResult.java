package net.atmos.overlay;

/**
 * Output of one behaviour evaluation: the desired accumulation target on
 * the Atmos 0-10 scale, and a multiplier applied on top of the material's
 * existing build/clear rate (e.g. season strength speeding up snow build,
 * heat speeding up wetness drying). 1.0 = no adjustment.
 */
public record OverlayTargetResult(float desiredTarget, float rateMultiplier) {
    public OverlayTargetResult {
        if (!Float.isFinite(desiredTarget)) {
            throw new IllegalArgumentException("desiredTarget must be finite");
        }
        if (!Float.isFinite(rateMultiplier) || rateMultiplier <= 0f) {
            throw new IllegalArgumentException("rateMultiplier must be positive and finite");
        }
    }
}