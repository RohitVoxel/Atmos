package net.atmos.confidence;

/**
 * Confidence System orchestrator (Chapter 4).
 *
 * Stateless by design in this task: evaluate() is a pure function of its
 * inputs, with no temporal memory, no prediction, and no hero-selection
 * awareness. Chapter 4 §11-17 describe those capabilities (Confidence
 * Memory, Predictive Confidence, Hero Confidence) as part of the same
 * chapter, but each depends on systems explicitly excluded from this task:
 *
 *   - Confidence Memory / temporal blending needs the Atmospheric
 *     Transition State (Chapter 5) — not yet built.
 *   - Predictive Confidence needs Cell Grid streaming-ahead-of-player
 *     prediction — not part of the approved Cell Grid scope.
 *   - Hero Confidence needs the Composition Engine (Chapter 10) — not yet
 *     built, and explicitly excluded from this task.
 *
 * Building any of these now would mean anticipating an unbuilt system's
 * shape or duplicating logic that belongs to it — both forbidden by scope.
 * They are logged as future enhancements, to be added once their
 * dependencies exist, without needing to touch this class's core
 * evaluate() contract.
 *
 * Per Chapter 4 §9 ("Confidence Is Deterministic"): identical inputs always
 * produce an identical result. No randomness, no hidden state, anywhere
 * in this evaluation path.
 */
public final class ConfidenceSystem {

    private ConfidenceSystem() {}

    public static Confidence evaluate(ConfidenceInputs inputs) {
        TierAResult tierA = TierAEvaluator.evaluate(inputs.env());
        TierBResult tierB = TierBEvaluator.evaluate(inputs.cell());
        TierCResult tierC = TierCEvaluator.evaluate(inputs.camera(), inputs.targetWorldPos());

        // Top-level combination: straight multiplication, per Chapter 4 §3
        // and Appendix B §3 — deliberately NOT ConfidenceMath's weighted
        // geometric product, which is reserved for within-tier combination.
        float finalValue = tierA.value() * tierB.value() * tierC.value();

        return new Confidence(tierA, tierB, tierC, finalValue);
    }
}