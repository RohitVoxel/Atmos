package net.atmos.pes;

/** Breakdown of one Pattern Repetition evaluation (§12.28). */
public record PatternRepetitionResult(
        int sampledHeroEntries,
        int mostFrequentHeroCount,
        float repetitionRatio,
        boolean repetitive
) {}