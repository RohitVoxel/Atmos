package net.atmos.exposure;

/**
 * Centralized tuning constants for Chapter 14 Exposure Model logic.
 * No Exposure Model class may declare its own tuning constant.
 */
public final class ExposureWeights {

    private ExposureWeights() {}

    /**
     * Identity exposure multiplier — "no adaptation applied." Matches the
     * baseline convention already used by
     * {@code DirectorWeights.GLOBAL_INTENSITY_BASELINE} and
     * {@code DirectorWeights.OPTIMIZATION_PLAN_FAILSAFE_BUDGET}. Stage 1's
     * only defined value; §14.6–§14.7's luminance and adaptation-speed
     * constants remain undefined until that algorithm is implemented.
     */
    public static final float EXPOSURE_BASELINE = 1.0f;
}