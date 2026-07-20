package net.atmos.seasonal;

/**
 * Seasonal Clock — Appendix X §6, frozen pipeline (Appendix X Rev 2.4 §1),
 * extended by Revision 2.7 §2 (Deterministic Cycle Length Definitions).
 *
 * Sole owner of ALL cycle-length and cycle-progress arithmetic in the
 * Seasonal Feeling System (review correction): both {@link #progress} and
 * {@link #deriveMoistureCycleLength} live here so that
 * {@link SeasonalProfileModel} — a pure trigonometric consumer — never
 * needs to perform its own progress-fraction computation.
 *
 * cycleLengthTicks is a caller-supplied parameter, not a baked-in
 * constant — Appendix X §6 marks the cycle length "implementation-
 * defined," an Architect decision not yet supplied.
 */
public final class SeasonalClock {

    private SeasonalClock() {}

    public static float progress(long worldTimeTicks, long cycleLengthTicks) {
        if (cycleLengthTicks <= 0L) {
            throw new IllegalArgumentException(
                    "cycleLengthTicks must be positive, got " + cycleLengthTicks);
        }
        long wrapped = Math.floorMod(worldTimeTicks, cycleLengthTicks);
        return (float) wrapped / (float) cycleLengthTicks;
    }

    /**
     * Derives the moisture axis's cycle length from the thermal cycle
     * length, per Appendix X Revision 2.7 §2:
     * {@code round(thermalCycleLengthTicks * MOISTURE_CYCLE_SCALAR)}.
     * Owned here, not by SeasonalProfileModel — Seasonal Clock is the
     * sole owner of cycle-length arithmetic (review correction).
     */
    public static long deriveMoistureCycleLength(long thermalCycleLengthTicks) {
        if (thermalCycleLengthTicks <= 0L) {
            throw new IllegalArgumentException(
                    "thermalCycleLengthTicks must be positive, got " + thermalCycleLengthTicks);
        }
        return Math.round(thermalCycleLengthTicks * SFSConstants.MOISTURE_CYCLE_SCALAR);
    }
}