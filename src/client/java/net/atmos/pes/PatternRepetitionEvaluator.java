package net.atmos.pes;

/**
 * Pattern Repetition evaluator — Chapter 12 §12.28.
 *
 * Flags repetition when the same Hero Cluster anchor coordinate recurs
 * across a disproportionate fraction of sampled frames — §12.28's own
 * example ("primary atmospheric features appearing in the exact same
 * spatial configuration repeatedly"). §12.28's second example (repeated
 * transition curves) is not evaluated — no exposure/curve data exists.
 *
 * Reads only the buffer's incrementally-maintained hero-anchor counts
 * (O(1) lookups, no per-frame allocation and no full-buffer rescan) and
 * folds the not-yet-pushed current frame in arithmetically. Requires at
 * least PATTERN_REPETITION_MIN_SAMPLES Hero-bearing frames before
 * flagging, to avoid false positives on a sparsely populated buffer.
 */
public final class PatternRepetitionEvaluator {

    private PatternRepetitionEvaluator() {}

    public static PatternRepetitionResult evaluate(PESHistoryView history, PESHistoryEntry current) {
        int sampled = history.heroBearingEntryCount();
        int mostFrequent = history.mostFrequentHeroAnchorCount();

        if (current.heroAnchor() != null) {
            int currentAnchorCount = history.heroAnchorCount(current.heroAnchor()) + 1;
            mostFrequent = Math.max(mostFrequent, currentAnchorCount);
            sampled++;
        }

        if (sampled < PESWeights.PATTERN_REPETITION_MIN_SAMPLES) {
            return new PatternRepetitionResult(sampled, mostFrequent, 0f, false);
        }

        float ratio = (float) mostFrequent / sampled;
        return new PatternRepetitionResult(sampled, mostFrequent, ratio,
                ratio > PESWeights.PATTERN_REPETITION_RATIO_THRESHOLD);
    }
}