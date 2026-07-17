package net.atmos.exposure;

/**
 * Immutable, versioned output of the Exposure Model — Chapter 14 §14.12.
 *
 * Published exclusively by {@link ExposureStateManager} via lock-free
 * atomic reference publication. {@code version} is a monotonically
 * increasing publication ordinal; per §14.12 it "carries no simulation
 * meaning" and exists solely for deterministic ordering, downstream
 * synchronization, and diagnostic validation — never for exposure
 * calculation.
 *
 * {@code exposureScale} is the finalized exposure multiplier consumed by
 * RenderCluster Construction (§14.20 Step 6). Stage 1 defines only this
 * finalized output contract; per-factor explainability (luminance, biome,
 * weather terms) is a Stage 2 concern once §14.6–§14.7 are implemented.
 */
public record ExposureStateSnapshot(
        long version,
        float exposureScale
) {
    public ExposureStateSnapshot {
        if (!Float.isFinite(exposureScale) || exposureScale < 0f) {
            throw new IllegalArgumentException(
                    "exposureScale must be a non-negative finite value, got " + exposureScale);
        }
    }
}