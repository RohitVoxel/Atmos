package net.atmos.seasonal;

import java.util.Objects;

/**
 * Immutable published output of the Seasonal Feeling System — Chapter 15,
 * Appendix X Revision 2.4 §11, extended Phase 1 (365-day calendar, named
 * seasons, season progress/strength, explicit influence channels).
 *
 * The original eight fields are unchanged in semantics and ownership.
 * calendar/influence are additive Phase 1 fields.
 */
public record SeasonalFeelingSnapshot(
        float seasonalProgress,
        MacroMood macroMood,
        MicroMood microMood,
        float thermalTendency,
        float moistureTendency,
        float densityBias,
        float clarityBias,
        float volatility,
        SeasonalCalendarResult calendar,
        SeasonalInfluenceResult influence
) {
    public SeasonalFeelingSnapshot {
        if (!Float.isFinite(seasonalProgress) || seasonalProgress < 0f || seasonalProgress > 1f) {
            throw new IllegalArgumentException(
                    "seasonalProgress must be within [0,1], got " + seasonalProgress);
        }
        if (macroMood == null) throw new IllegalArgumentException("macroMood must not be null");
        if (microMood == null) throw new IllegalArgumentException("microMood must not be null");
        requireFinite("thermalTendency", thermalTendency);
        requireFinite("moistureTendency", moistureTendency);
        requireFinite("densityBias", densityBias);
        requireFinite("clarityBias", clarityBias);
        requireFinite("volatility", volatility);
        calendar  = Objects.requireNonNullElseGet(calendar, SeasonalCalendarResult::neutral);
        influence = Objects.requireNonNullElseGet(influence, SeasonalInfluenceResult::neutral);
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
    }

    /** Neutral sentinel per Appendix X §14/§31 — published on init and for unsupported dimensions. */
    public static SeasonalFeelingSnapshot neutral() {
        return new SeasonalFeelingSnapshot(
                SFSConstants.NEUTRAL_SEASONAL_PROGRESS,
                MacroMood.NEUTRAL,
                MicroMood.NEUTRAL,
                SFSConstants.NEUTRAL_THERMAL_TENDENCY,
                SFSConstants.NEUTRAL_MOISTURE_TENDENCY,
                SFSConstants.NEUTRAL_DENSITY_BIAS,
                SFSConstants.NEUTRAL_CLARITY_BIAS,
                SFSConstants.NEUTRAL_VOLATILITY,
                SeasonalCalendarResult.neutral(),
                SeasonalInfluenceResult.neutral()
        );
    }
}