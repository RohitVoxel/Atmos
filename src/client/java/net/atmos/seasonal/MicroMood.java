package net.atmos.seasonal;

/**
 * Localized daily-rhythm emotional state — Chapter 15 §15.12-§15.15,
 * Appendix X §8-9.
 *
 * Stage 1 defines only NEUTRAL, the sentinel value consumed by
 * {@link SeasonalFeelingSnapshot#neutral()}. The full daily mood
 * vocabulary (Morning/Afternoon/Evening/Night identities) depends on Daily
 * Rhythm math (Appendix X §8), which is Stage 2 work.
 */
public enum MicroMood {
    NEUTRAL
}