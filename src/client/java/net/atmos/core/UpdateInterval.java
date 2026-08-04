package net.atmos.core;

/**
 * Batch 1 Phase 3 — per-subsystem update-interval gate.
 *
 * A subsystem calls shouldRun(currentTick) before doing any work. If false,
 * the subsystem returns immediately without recomputation. Interval is
 * measured in ticks (20/sec), never frames, so behavior is identical at
 * any FPS.
 */
public final class UpdateInterval {

    private final int intervalTicks;
    private long lastRunTick = Long.MIN_VALUE;

    public UpdateInterval(int intervalTicks) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be >= 1, got " + intervalTicks);
        }
        this.intervalTicks = intervalTicks;
    }

    public boolean shouldRun(long currentTick) {
        if (lastRunTick == Long.MIN_VALUE || currentTick - lastRunTick >= intervalTicks) {
            lastRunTick = currentTick;
            return true;
        }
        return false;
    }

    public void reset() {
        lastRunTick = Long.MIN_VALUE;
    }
}