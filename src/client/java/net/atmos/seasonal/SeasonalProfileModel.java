package net.atmos.seasonal;

/**
 * Seasonal Profile Model — Appendix X Revision 2.7 §1, §6, Stage 4.
 *
 * Pure trigonometric mapping only (review correction): consumes two
 * already-resolved progress fractions — thermalProgress, moistureProgress
 * — plus their per-world phase offsets, and produces
 * thermalTendency/moistureTendency in [-1,1]. Performs NO cycle-length
 * arithmetic and calls {@link SeasonalClock} zero times; all progress
 * resolution is owned exclusively by SeasonalClock and performed upstream
 * by the caller ({@link SeasonalFeelingSystem}), preserving the frozen
 * feed-forward pipeline:
 *
 *     Seasonal Clock -> progress -> Seasonal Profile Model
 *
 * Independence between the two axes is achieved entirely upstream, via
 * two combined mechanisms this class does not itself implement: different
 * cycle lengths (thermal vs. moisture, derived by
 * {@link SeasonalClock#deriveMoistureCycleLength}) and per-world phase
 * offsets ({@link SeasonalSeedHash}).
 *
 * No clamping is applied to the cosine outputs: {@code Math.cos} on any
 * finite {@code double} is guaranteed by the Java Language Specification
 * to return a value in [-1,1] — a defensive clamp here would be dead code
 * (review correction; a prior revision applied one unnecessarily).
 *
 * Pure, stateless, O(1). No allocation beyond the returned record.
 */
public final class SeasonalProfileModel {

    private SeasonalProfileModel() {}

    public static SeasonalProfileResult evaluate(float thermalProgress,
                                                 float moistureProgress,
                                                 float thermalPhaseOffsetRadians,
                                                 float moisturePhaseOffsetRadians) {
        float thermalTendency = (float) Math.cos(
                thermalProgress * SFSConstants.TWO_PI + thermalPhaseOffsetRadians);
        float moistureTendency = (float) Math.cos(
                moistureProgress * SFSConstants.TWO_PI + moisturePhaseOffsetRadians);

        return new SeasonalProfileResult(thermalTendency, moistureTendency);
    }
}