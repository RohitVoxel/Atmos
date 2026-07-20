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

    /**
     * Appendix X Revision 2.7 §2 — golden-ratio scalar used by
     * {@link SeasonalClock#deriveMoistureCycleLength} to derive the
     * moisture cycle length from the thermal cycle length:
     * {@code Math.round(thermalCycleLengthTicks * MOISTURE_CYCLE_SCALAR)}.
     * Fixed value per Rohit's explicit specification — not implementation-defined.
     */
    public static final float MOISTURE_CYCLE_SCALAR = 1.6180339f;
}