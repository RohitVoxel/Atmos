package net.atmos.seasonal;

/**
 * Macro Mood evaluation — Chapter 15 §15.23, Appendix X §9.
 *
 * Wires SeasonalClock's continuous progress into mood evaluation. Per
 * MacroMood's own class doc, the full seasonal mood vocabulary requires
 * explicit Architect confirmation of the exact enum set — Chapter 15
 * gives only illustrative mood words, not a closed list. Until that
 * confirmation exists, every seasonalProgress deterministically maps to
 * MacroMood.NEUTRAL, establishing the pipeline shape without inventing
 * unapproved vocabulary.
 */
public final class MacroMoodEvaluator {

    private MacroMoodEvaluator() {}

    public static MacroMood evaluate(float seasonalProgress) {
        return MacroMood.NEUTRAL;
    }
}