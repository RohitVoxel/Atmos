package net.atmos.atmosphere.sky;

/**
 * Named sky phase derived from sun elevation in degrees.
 * Boundaries follow standard twilight definitions (0 / -6 / -12 / -18).
 */
public enum SkyPhase {
    DAY,
    GOLDEN_HOUR,
    CIVIL_TWILIGHT,
    NAUTICAL_TWILIGHT,
    ASTRONOMICAL_TWILIGHT,
    NIGHT;

    public static final float DAY_START_DEGREES           = 10f;
    public static final float CIVIL_TWILIGHT_START_DEGREES = 0f;
    public static final float NAUTICAL_TWILIGHT_START_DEGREES = -6f;
    public static final float ASTRONOMICAL_TWILIGHT_START_DEGREES = -12f;
    public static final float NIGHT_START_DEGREES = -18f;

    public static SkyPhase fromElevationDegrees(float elevationDegrees) {
        if (elevationDegrees >= DAY_START_DEGREES) return DAY;
        if (elevationDegrees >= CIVIL_TWILIGHT_START_DEGREES) return GOLDEN_HOUR;
        if (elevationDegrees >= NAUTICAL_TWILIGHT_START_DEGREES) return CIVIL_TWILIGHT;
        if (elevationDegrees >= ASTRONOMICAL_TWILIGHT_START_DEGREES) return NAUTICAL_TWILIGHT;
        if (elevationDegrees >= NIGHT_START_DEGREES) return ASTRONOMICAL_TWILIGHT;
        return NIGHT;
    }
}