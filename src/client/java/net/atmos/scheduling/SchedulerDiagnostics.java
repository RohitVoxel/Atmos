package net.atmos.scheduling;

/**
 * Read-only diagnostic snapshot of one {@link AdaptivePriorityScheduler}.
 * Every field reflects actual measured runtime state — nothing estimated
 * or fabricated beyond the scheduler's own documented EMA cost estimate.
 */
public record SchedulerDiagnostics(
        int pendingCount,
        long totalSubmitted,
        long totalProcessed,
        long lastDrainNanos,
        int lastDrainCount,
        double averageItemNanos
) {
    public static SchedulerDiagnostics capture(AdaptivePriorityScheduler<?> scheduler) {
        return new SchedulerDiagnostics(
                scheduler.pendingCount(),
                scheduler.totalSubmitted(),
                scheduler.totalProcessed(),
                scheduler.lastDrainNanos(),
                scheduler.lastDrainCount(),
                scheduler.averageItemNanos()
        );
    }
}