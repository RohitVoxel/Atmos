package net.atmos.render;

/**
 * Explainable result of one Final Alpha Assembly evaluation (Appendix ZB
 * Blocker 9).
 *
 * rawAlpha   — A_raw, the raw optical-density product.
 * finalAlpha — A_final, the Beer-Lambert-mapped result. Self-bounded
 *              within [0,1) for any finite, non-negative rawAlpha by
 *              the exponential mapping itself.
 *
 * Deliberately unvalidated, mirroring
 * {@code net.atmos.sunreach.SunReachCombinationResult}'s identical
 * bare-record precedent — the nearest architectural sibling (both are
 * terminal "Final X Combination" producers).
 */
public record FinalAlphaResult(
        float rawAlpha,
        float finalAlpha
) {}