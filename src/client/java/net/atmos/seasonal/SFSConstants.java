package net.atmos.seasonal;

/**
 * Centralized tuning constants for Chapter 15 Seasonal Feeling System.
 * No SFS class may declare its own tuning constant EXCEPT where a
 * transfer-function coefficient is explicitly encapsulated within its
 * owning evaluator per Appendix X Revision 2.7's review correction (see
 * {@link ContinuousBiasGenerator}) — those remain implementation-defined
 * and are deliberately kept private, not published here.
 *
 * THERMAL_CYCLE_LENGTH_TICKS is deliberately absent — no numeric value is
 * assigned anywhere in the Master Guide or Appendix X for it; this is an
 * Architect decision (Rohit), not a value Claude may invent. It is
 * supplied once, at {@link SeasonalFeelingSystem#initialize} time — see
 * that class's doc.
 */
public final class SFSConstants {

    private SFSConstants() {}

    public static final float NEUTRAL_SEASONAL_PROGRESS = 0f;
    public static final float NEUTRAL_THERMAL_TENDENCY   = 0f;
    public static final float NEUTRAL_MOISTURE_TENDENCY  = 0f;
    public static final float NEUTRAL_DENSITY_BIAS       = 0f;
    public static final float NEUTRAL_CLARITY_BIAS       = 0f;
    public static final float NEUTRAL_VOLATILITY         = 0f;

    /** Full seasonal cycle expressed in radians — Appendix X §7 periodicity. */
    public static final float TWO_PI = (float) (2.0 * Math.PI);

    public static final float MOISTURE_CYCLE_SCALAR = 1.6180339f;

    /** Phase 1 — 365-day seasonal calendar, per explicit Architect specification. */
    public static final long YEAR_LENGTH_TICKS = 24_000L * 365L;
}