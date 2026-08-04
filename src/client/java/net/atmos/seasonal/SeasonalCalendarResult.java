package net.atmos.seasonal;

/** Named-season identity and smooth intra-season position — Phase 1. */
public record SeasonalCalendarResult(
        Season currentSeason,
        Season nextSeason,
        float seasonProgress,
        float seasonStrength,
        float yearProgress
) {
    public SeasonalCalendarResult {
        if (currentSeason == null) throw new IllegalArgumentException("currentSeason must not be null");
        if (nextSeason == null) throw new IllegalArgumentException("nextSeason must not be null");
        requireUnit("seasonProgress", seasonProgress);
        requireUnit("seasonStrength", seasonStrength);
        requireUnit("yearProgress", yearProgress);
    }

    private static void requireUnit(String name, float v) {
        if (!Float.isFinite(v) || v < 0f || v > 1f) {
            throw new IllegalArgumentException(name + " must be within [0,1], got " + v);
        }
    }

    public static SeasonalCalendarResult neutral() {
        return new SeasonalCalendarResult(Season.SPRING, Season.SUMMER, 0f, 0f, 0f);
    }
}