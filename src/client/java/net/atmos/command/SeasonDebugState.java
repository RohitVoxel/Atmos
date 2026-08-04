package net.atmos.command;

/**
 * Debug-only season time override. Never touches SeasonalClock,
 * SeasonalProfileModel, or any frozen Seasonal Feeling System math —
 * it only substitutes the worldTimeTicks value handed to
 * SeasonalFeelingSystem.update() at the call site.
 */
public final class SeasonDebugState {

    private SeasonDebugState() {}

    private static boolean overrideActive = false;
    private static long overrideWorldTimeTicks = 0L;
    private static boolean paused = false;
    private static long pausedWorldTimeTicks = 0L;

    public static long resolveWorldTime(long actualWorldTimeTicks) {
        if (paused) return pausedWorldTimeTicks;
        if (overrideActive) return overrideWorldTimeTicks;
        return actualWorldTimeTicks;
    }

    public static void setOverride(long worldTimeTicks) {
        overrideActive = true;
        paused = false;
        overrideWorldTimeTicks = worldTimeTicks;
    }

    public static void clearOverride() {
        overrideActive = false;
    }

    public static void pause(long currentEffectiveTime) {
        paused = true;
        pausedWorldTimeTicks = currentEffectiveTime;
    }

    public static void resume() {
        paused = false;
    }

    public static boolean isPaused() { return paused; }
    public static boolean isOverridden() { return overrideActive; }

    public static void reset() {
        overrideActive = false;
        paused = false;
        overrideWorldTimeTicks = 0L;
        pausedWorldTimeTicks = 0L;
    }
}
