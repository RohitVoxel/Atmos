package net.atmos.seasonal;

// 365-day Seasonal Calendar — Phase 1. Maps SeasonalClock's continuous
// yearProgress [0,1) onto four equal-length named seasons.


public final class SeasonalCalendar {

    private SeasonalCalendar() {}

    private static final float QUARTER = 0.25f;

    public static SeasonalCalendarResult evaluate(float yearProgress) {
        float normalized = yearProgress - (float) Math.floor(yearProgress);

        int seasonIndex = Math.min(3, (int) (normalized / QUARTER));
        Season current = Season.values()[seasonIndex];
        Season next = current.next();

        float seasonProgress = (normalized - seasonIndex * QUARTER) / QUARTER;
        float seasonStrength = 0.5f - 0.5f * (float) Math.cos(seasonProgress * SFSConstants.TWO_PI);

        return new SeasonalCalendarResult(current, next, seasonProgress, seasonStrength, normalized);
    }
}