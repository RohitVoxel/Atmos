package net.atmos.seasonal;

/**
 * Micro Mood evaluation — Chapter 15 §15.12-15.15, Appendix X §8-9.
 *
 * Wires DailyRhythm's continuous progress into mood evaluation. The
 * daily mood vocabulary (Morning/Afternoon/Evening/Night identities)
 * remains blocked pending the same Architect confirmation required by
 * MacroMoodEvaluator. Every dailyProgress deterministically maps to
 * MicroMood.NEUTRAL until that confirmation exists.
 */
public final class MicroMoodEvaluator {

    private MicroMoodEvaluator() {}

    public static MicroMood evaluate(float dailyProgress) {
        return MicroMood.NEUTRAL;
    }
}