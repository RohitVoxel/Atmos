package net.atmos.memory;

/**
 * Appendix F 2.0 §1 — IO Lifecycle State Machine.
 *
 * LOADED is deliberately not represented as an enum value: it corresponds
 * to the absence of any tracking entry (the cell is live and exclusively
 * Simulation-Thread-owned, not queued for persistence). Once a tracking
 * entry exists it transitions strictly forward:
 *
 *     PENDING_WRITE -> WRITING -> WRITTEN
 *
 * Cancellation during PENDING_WRITE (§13.15) removes the tracking entry
 * entirely rather than introducing a fifth observable state — no
 * intermediate state ever becomes externally observable, per §13.14.
 */
enum CellMemoryIoState {
    PENDING_WRITE,
    WRITING,
    WRITTEN
}