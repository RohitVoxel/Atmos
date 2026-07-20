package net.atmos.seasonal;

/**
 * Immutable published output of the Seasonal Feeling System — Chapter 15,
 * Appendix X Revision 2.4 §11.
 *
 * Exactly one snapshot exists per publication (§11 "Publication"). All
 * fields are strictly immutable; the snapshot remains valid until replaced
 * by the next snapshot published via {@link SeasonalFeelingStateManager}.
 *
 * seasonalProgress / macroMood / microMood are direct outputs of the frozen
 * Stage 2 pipeline (Seasonal Clock / Daily Rhythm -> Atmospheric Mood). The
 * five tendency/bias fields remain neutral placeholders (SFSConstants.NEUTRAL_*)
 * pending Continuous Bias Generation (Appendix X §7) — out of scope for Stage 2.
 *
 * Sign convention for thermalTendency, moistureTendency, densityBias,
 * clarityBias, and volatility is not yet Architect-confirmed (see
 * SFSConstants class doc) — only finiteness is validated here, not range.
 */
public record SeasonalFeelingSnapshot(
        float seasonalProgress,
        MacroMood macroMood,
        MicroMood microMood,
        float thermalTendency,
        float moistureTendency,
        float densityBias,
        float clarityBias,
        float volatility
) {
    public SeasonalFeelingSnapshot {
        if (!Float.isFinite(seasonalProgress) || seasonalProgress < 0f || seasonalProgress > 1f) {
            throw new IllegalArgumentException(
                    "seasonalProgress must be within [0,1], got " + seasonalProgress);
        }
        if (macroMood == null) {
            throw new IllegalArgumentException("macroMood must not be null");
        }
        if (microMood == null) {
            throw new IllegalArgumentException("microMood must not be null");
        }
        requireFinite("thermalTendency", thermalTendency);
        requireFinite("moistureTendency", moistureTendency);
        requireFinite("densityBias", densityBias);
        requireFinite("clarityBias", clarityBias);
        requireFinite("volatility", volatility);
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
                SFSConstants.NEUTRAL_VOLATILITY
        );
    }
}