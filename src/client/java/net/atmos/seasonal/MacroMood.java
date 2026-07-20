package net.atmos.seasonal;

/**
 * Overarching seasonal emotional state — Chapter 15 §15.23, Appendix X §9.
 *
 * Stage 1 defines only NEUTRAL, the sentinel value consumed by
 * {@link SeasonalFeelingSnapshot#neutral()}. The full seasonal mood
 * vocabulary (Spring/Summer/Wet Season/Cold Season identities described in
 * Chapter 15 §15.23) is Stage 2 "Atmospheric Mood Architecture" work
 * (Appendix X §9) and requires explicit confirmation of the exact enum set
 * before implementation — the Master Guide gives illustrative mood words
 * per season rather than a closed authoritative list.
 */
public enum MacroMood {
    NEUTRAL
}