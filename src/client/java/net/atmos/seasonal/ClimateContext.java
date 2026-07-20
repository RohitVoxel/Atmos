package net.atmos.seasonal;

/**
 * Static, read-only per-dimension configuration — Appendix X §10.
 *
 * "Climate Context is a static, read-only configuration defined by
 * immutable dimension characteristics supplied during initialization."
 * Supplied once per dimension load; never queried per-tick, never derived
 * from a live world/block/chunk query (§10).
 *
 * World seed is deliberately NOT a field here (review correction):
 * Appendix X §10 only names "dimension characteristics" as this record's
 * scope, and Rev 2.7 §3 assigns worldSeed only to the Seed Hash Utility's
 * input — never to ClimateContext ownership. worldSeed is instead passed
 * directly to {@link SeasonalFeelingSystem#initialize}.
 */
public record ClimateContext(
        String dimensionKey,
        boolean seasonalCycleSupported
) {
    /** Sentinel used before {@link SeasonalFeelingSystem#initialize} has ever been called. */
    public static final ClimateContext UNINITIALIZED = new ClimateContext("uninitialized", false);

    private static final String OVERWORLD_KEY = "minecraft:overworld";

    public ClimateContext {
        if (dimensionKey == null || dimensionKey.isEmpty()) {
            throw new IllegalArgumentException("dimensionKey must not be null or empty");
        }
    }

    /**
     * Convenience factory deriving a ClimateContext from a dimension key.
     * Called once at dimension-load time by the Dimension Initialization
     * code path (future AtmosClient integration) — never per-tick.
     */
    public static ClimateContext forDimension(String dimensionKey) {
        return new ClimateContext(dimensionKey, OVERWORLD_KEY.equals(dimensionKey));
    }
}