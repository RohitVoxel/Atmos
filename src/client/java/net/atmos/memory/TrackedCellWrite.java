package net.atmos.memory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable-by-CAS tracking record for one enqueued write (Appendix F 2.0
 * §2–§3). The snapshot itself is immutable and never changes after
 * construction; only {@code state} is ever mutated, exclusively via CAS.
 */
final class TrackedCellWrite {

    private final CellMemoryKey key;
    private final CellMemorySnapshot snapshot;
    private final AtomicReference<CellMemoryIoState> state =
            new AtomicReference<>(CellMemoryIoState.PENDING_WRITE);

    TrackedCellWrite(CellMemoryKey key, CellMemorySnapshot snapshot) {
        this.key = key;
        this.snapshot = snapshot;
    }

    CellMemoryKey key() { return key; }

    CellMemorySnapshot snapshot() { return snapshot; }

    boolean compareAndSetState(CellMemoryIoState expected, CellMemoryIoState next) {
        return state.compareAndSet(expected, next);
    }
}