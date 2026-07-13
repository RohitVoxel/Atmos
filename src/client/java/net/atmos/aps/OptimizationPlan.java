package net.atmos.aps;

/**
 * Immutable performance budget snapshot — Appendix T §T.4.
 *
 * Published by the Adaptive Performance System (Chapter 16, not yet
 * implemented). Consumed read-only by
 * {@link net.atmos.director.AtmosphereDirector} via
 * {@link net.atmos.director.DirectorPerformanceEvaluator}.
 *
 * atmosphereBudget — 1.0 = no reduction requested, 0.0 = maximum
 * reduction requested. No validation is performed here; clamping of
 * out-of-range or non-finite values is the consumer's responsibility
 * (§T.7, §T.23–§T.24), so this contract does not constrain a future APS
 * producer.
 *
 * Per §T.26 this record may gain additional fields once Chapter 16 (and
 * the richer ALSC-facing shape referenced in Appendix D §2 / Appendix A
 * §7.2) is implemented. Only atmosphereBudget is required for Stage 8;
 * existing consumers must ignore unknown future fields.
 */
public record OptimizationPlan(float atmosphereBudget) {}