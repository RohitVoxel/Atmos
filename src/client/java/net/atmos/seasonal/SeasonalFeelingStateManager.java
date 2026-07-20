package net.atmos.seasonal;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free publisher for {@link SeasonalFeelingSnapshot} — Appendix X §12.
 *
 * Mirrors the identical publish/get/reset pattern already established by
 * {@code net.atmos.core.CameraManager}, {@code net.atmos.exposure.ExposureStateManager},
 * and {@code net.atmos.pes.PerceptualReportManager}.
 *
 * Ownership: exactly one writer, {@link SeasonalFeelingSystem}
 * (Simulation Thread, per §12). Any number of readers, from any thread,
 * may safely call {@link #get()} without synchronization.
 *
 * Unlike CameraManager/PerceptualReportManager, {@link #get()} never
 * returns null: SeasonalFeelingSnapshot.neutral() is always a valid
 * published state, per Appendix X §14's "publish a neutral snapshot"
 * failure-handling contract.
 */
public final class SeasonalFeelingStateManager {

    private SeasonalFeelingStateManager() {}

    private static final AtomicReference<SeasonalFeelingSnapshot> CURRENT =
            new AtomicReference<>(SeasonalFeelingSnapshot.neutral());

    /** Sole writer: {@link SeasonalFeelingSystem}. */
    static void publish(SeasonalFeelingSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        CURRENT.set(snapshot);
    }

    /** Latest published snapshot. Never null. */
    public static SeasonalFeelingSnapshot get() {
        return CURRENT.get();
    }

    /** Resets to neutral — same lifecycle points as every other Atmos controller's reset(). */
    public static void reset() {
        CURRENT.set(SeasonalFeelingSnapshot.neutral());
    }
}