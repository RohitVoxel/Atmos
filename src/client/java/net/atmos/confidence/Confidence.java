package net.atmos.confidence;

/**
 * Final, fully explainable result of one Confidence System evaluation.
 *
 * Per Chapter 4 §3 ("Final Confidence"), the three tier values combine by
 * straight multiplication — NOT the weighted-geometric-product formula
 * used internally within each tier (Appendix D §7). That formula is a
 * within-tier combination rule only; the top-level Tier A × Tier B × Tier C
 * multiplication is a separate, simpler rule confirmed identically by both
 * Chapter 4 §3 and Appendix B §3.
 *
 * Every per-tier breakdown is retained (not just the final scalar) per
 * Chapter 4 §9/§20's explainability requirement — a future debug overlay
 * can render this directly without recomputing anything.
 */
public record Confidence(
        TierAResult tierA,
        TierBResult tierB,
        TierCResult tierC,
        float finalValue
) {}