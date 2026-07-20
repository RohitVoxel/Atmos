package net.atmos.seasonal;

/**
 * Daily Rhythm — Appendix X §8, frozen pipeline (Appendix X Rev 2.4 §1).
 *
 * Maps world time to a continuous, seamlessly-wrapping dailyProgress in
 * [0,1) across Minecraft's fixed 24,000-tick day. Per the corrected
 * architecture, this class outputs only the raw continuous progress —
 * it does NOT produce microMood. Mood evaluation is owned exclusively
 * by AtmosphericMood, which consumes this dailyProgress value.
 *
 * Midnight wrap-around (24000 -> 0) is mathematically continuous via
 * Math.floorMod. Stateless, deterministic, O(1).
 */
public final class DailyRhythm {

    private DailyRhythm() {}

    public static final long DAY_LENGTH_TICKS = 24_000L;

    public static float progress(long worldTimeTicks) {
        long wrapped = Math.floorMod(worldTimeTicks, DAY_LENGTH_TICKS);
        return (float) wrapped / (float) DAY_LENGTH_TICKS;
    }
}