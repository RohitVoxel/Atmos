package net.atmos.overlay;

import net.atmos.seasonal.Season;
import net.atmos.seasonal.SeasonalFeelingSnapshot;

/**
 * Publishes overlay targets from the existing Seasonal Feeling System.
 * Never recomputes seasonal state — reads SeasonalFeelingSnapshot.calendar()
 * only, which the Seasonal Feeling System already owns and publishes.
 */
public final class OverlaySeasonalPublisher {

    private static final float FROST_SNOW_RATIO   = 0.85f;
    private static final float DUST_SUMMER_RATIO  = 0.40f;

    private OverlaySeasonalPublisher() {}

    public static void publish(OverlayManager overlayManager, SeasonalFeelingSnapshot snapshot) {
        Season current = snapshot.calendar().currentSeason();
        Season next    = snapshot.calendar().nextSeason();
        float progress = snapshot.calendar().seasonProgress();

        float snowTarget   = seasonWeight(current, next, progress, Season.WINTER);
        float frostTarget  = snowTarget * FROST_SNOW_RATIO;
        float autumnTarget = seasonWeight(current, next, progress, Season.AUTUMN);
        float pollenTarget = seasonWeight(current, next, progress, Season.SPRING);
        float dustTarget   = seasonWeight(current, next, progress, Season.SUMMER) * DUST_SUMMER_RATIO;

        overlayManager.setContribution(OverlayType.SNOW,   OverlaySource.SEASON, snowTarget);
        overlayManager.setContribution(OverlayType.FROST,  OverlaySource.SEASON, frostTarget);
        overlayManager.setContribution(OverlayType.AUTUMN, OverlaySource.SEASON, autumnTarget);
        overlayManager.setContribution(OverlayType.POLLEN, OverlaySource.SEASON, pollenTarget);
        overlayManager.setContribution(OverlayType.DUST,   OverlaySource.SEASON, dustTarget);
    }

    private static float seasonWeight(Season current, Season next, float progress, Season target) {
        if (current == target) return 1f - progress;
        if (next == target) return progress;
        return 0f;
    }
}
