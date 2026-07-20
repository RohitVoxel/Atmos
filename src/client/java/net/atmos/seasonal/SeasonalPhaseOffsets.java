package net.atmos.seasonal;

/**
 * Deterministic per-world phase offsets for the thermal and moisture
 * axes, produced exclusively by {@link SeasonalSeedHash} — Appendix X
 * Revision 2.7 §3. Both fields are normalized to [0, TWO_PI) radians.
 * Consumed only by {@link SeasonalProfileModel}.
 */
public record SeasonalPhaseOffsets(
        float thermalPhaseOffsetRadians,
        float moisturePhaseOffsetRadians
) {
    /** Used only before a real world seed is available — see {@link ClimateContext#UNINITIALIZED}. */
    public static final SeasonalPhaseOffsets NEUTRAL = new SeasonalPhaseOffsets(0f, 0f);
}