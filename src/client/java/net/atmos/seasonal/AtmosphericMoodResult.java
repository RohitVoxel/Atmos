package net.atmos.seasonal;

/**
 * Immutable result of one Atmospheric Mood evaluation — Appendix X
 * Revision 2.4 §1. Carries both mood outputs together since Atmospheric
 * Mood is their single, combined producer.
 */
public record AtmosphericMoodResult(MacroMood macroMood, MicroMood microMood) {
    public AtmosphericMoodResult {
        if (macroMood == null) {
            throw new IllegalArgumentException("macroMood must not be null");
        }
        if (microMood == null) {
            throw new IllegalArgumentException("microMood must not be null");
        }
    }
}