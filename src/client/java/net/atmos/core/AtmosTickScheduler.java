package net.atmos.core;

/**
 * Batch 1 Phase 0/3 — phase-split, tick-driven scheduler.
 *
 * Replaces render-frame-coupled simulation. Driven by ClientTickEvents.END_CLIENT_TICK
 * (20 TPS, independent of render FPS). Work is split across a 4-tick rotation so no
 * single tick pays for every subsystem at once — this smooths tick time instead of
 * spiking it every 20th frame-equivalent.
 *
 * Each phase additionally checks its own interval divisor so a subsystem can run
 * less often than every 4 ticks (e.g. Director every 20 ticks) without needing a
 * second scheduler.
 */
public final class AtmosTickScheduler {

    public enum Phase { PIPELINE, OVERLAY, ENVIRONMENT, SLOW_SYSTEMS }

    private long tickCounter = 0L;

    /** Registered per-phase runnables. Populated once at init by AtmosClient. */
    private Runnable pipelineTask;
    private Runnable overlayTask;
    private Runnable environmentTask;
    private Runnable slowSystemsTask;

    public void register(Phase phase, Runnable task) {
        switch (phase) {
            case PIPELINE -> pipelineTask = task;
            case OVERLAY -> overlayTask = task;
            case ENVIRONMENT -> environmentTask = task;
            case SLOW_SYSTEMS -> slowSystemsTask = task;
        }
    }

    /** Call once per client tick. Never call from a render event. */
    public void tick() {
        int phase = (int) (tickCounter % 4L);
        tickCounter++;

        switch (phase) {
            case 0 -> { if (pipelineTask != null) pipelineTask.run(); }
            case 1 -> { if (overlayTask != null) overlayTask.run(); }
            case 2 -> { if (environmentTask != null) environmentTask.run(); }
            case 3 -> { if (slowSystemsTask != null) slowSystemsTask.run(); }
            default -> { }
        }
    }

    public long currentTick() { return tickCounter; }

    public void reset() { tickCounter = 0L; }
}