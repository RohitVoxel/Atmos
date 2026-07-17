package net.atmos.exposure;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free publisher for {@link ExposureStateSnapshot} — Chapter 14
 * §14.12 (Copy-on-Publish, atomic reference publication).
 *
 * Mirrors the identical publish/get/reset pattern already established by
 * {@code net.atmos.core.CameraManager} and
 * {@code net.atmos.pes.PerceptualReportManager}. Owns the monotonically
 * increasing version counter (§14.12) itself, matching CameraManager's
 * self-generated {@code frameSequence} rather than requiring the producer
 * to supply one — the version is a publication-layer ordering artifact,
 * not simulation output.
 *
 * Ownership: exactly one writer, {@link ExposureModel} (Simulation
 * Thread). Any number of readers, from any thread, may safely call
 * {@link #get()} without synchronization.
 */
public final class ExposureStateManager {

    private ExposureStateManager() {}

    private static final AtomicReference<ExposureStateSnapshot> CURRENT = new AtomicReference<>(null);
    private static final AtomicLong VERSION_SEQUENCE = new AtomicLong(0L);

    /** Constructs and publishes a new snapshot. Simulation Thread only; sole caller is {@link ExposureModel}. */
    static void publish(float exposureScale) {
        long version = VERSION_SEQUENCE.incrementAndGet();
        CURRENT.set(new ExposureStateSnapshot(version, exposureScale));
    }

    /** Latest published snapshot, or {@code null} before the first publish. */
    public static ExposureStateSnapshot get() {
        return CURRENT.get();
    }

    /** Clears the published snapshot — same lifecycle points as every other Atmos controller's reset(). */
    public static void reset() {
        CURRENT.set(null);
    }
}