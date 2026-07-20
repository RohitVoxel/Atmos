package net.atmos.seasonal;

/**
 * Atmospheric Mood — Chapter 15 §15.23, Appendix X Revision 2.4 §1
 * (corrected Chapter 15 §15.5 responsibility list).
 *
 * Sole owner of mood evaluation. Consumes both continuous progress
 * values (seasonalProgress, dailyProgress) and produces both outputs
 * (MacroMood, MicroMood) together — per the corrected architecture,
 * Daily Rhythm owns only the raw dailyProgress input consumed here; it
 * does not itself produce microMood.
 *
 * The full seasonal/daily mood vocabulary requires explicit Architect
 * confirmation of the exact enum sets (see MacroMood / MicroMood class
 * docs) — Chapter 15 gives only illustrative mood words, not a closed
 * list. Until that confirmation exists, every input combination
 * deterministically resolves to NEUTRAL, per this task's instruction:
 * "If the finalized mood vocabulary is still neutral-only, return the
 * approved neutral values without inventing additional moods."
 *
 * Stateless, deterministic, O(1). No world/block/chunk/biome/weather/
 * lighting queries — consumes only its two float parameters.
 */
public final class AtmosphericMood {

    private AtmosphericMood() {}

    public static AtmosphericMoodResult evaluate(float seasonalProgress, float dailyProgress) {
        return new AtmosphericMoodResult(MacroMood.NEUTRAL, MicroMood.NEUTRAL);
    }
}