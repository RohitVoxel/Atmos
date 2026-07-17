package net.atmos.pes;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free publisher for {@link PerceptualReport} — Chapter 12 §12.31
 * (Feed-Forward Loop) and §12.9 ("PES exclusively owns... The
 * PerceptualReport (published output)").
 *
 * Reuses the exact publish/get/reset AtomicReference pattern already
 * established by {@code net.atmos.core.CameraManager} (Appendix F §1)
 * rather than inventing a new synchronization mechanism.
 *
 * Ownership: Simulation Thread (§12.5). Exactly one writer —
 * {@link PerceptualEvaluationSystem#evaluate}, single call site, as the
 * final step of that method's own lifecycle (§12.39 Step 6). {@code
 * publish} is package-private: only PerceptualEvaluationSystem may
 * publish, enforced by the compiler rather than by convention alone.
 *
 * Any number of readers, on any thread, may safely call {@link #get()}.
 * The §12.31 one-frame lag is not enforced here — it is the natural
 * consequence of publication occurring once per simulation tick and a
 * consumer reading only the latest already-published value on a later
 * tick. This class never blocks, never re-evaluates, and never mutates
 * a published report.
 */
public final class PerceptualReportManager {

    private PerceptualReportManager() {}

    private static final AtomicReference<PerceptualReport> CURRENT = new AtomicReference<>(null);

    /** Sole writer: {@link PerceptualEvaluationSystem#evaluate}. */
    static void publish(PerceptualReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
        CURRENT.set(report);
    }

    /**
     * Returns the most recently published {@link PerceptualReport}, or
     * {@code null} if PES has not yet evaluated this session. Callers must
     * null-check, matching {@code CameraManager.get()}'s contract.
     */
    public static PerceptualReport get() {
        return CURRENT.get();
    }

    /**
     * Clears the published report. Intended for the same lifecycle points
     * as every other Atmos controller's reset() (disconnect, dimension
     * change) once a future task wires PES into that lifecycle — not
     * currently invoked anywhere, since PerceptualEvaluationSystem.evaluate()
     * itself has no live call site yet.
     */
    public static void reset() {
        CURRENT.set(null);
    }
}