package net.atmos.ui;

/**
 * Visual design tokens for the Atmos configuration interface.
 * Reflects the atmospheric identity of the mod: sky, sunset, twilight, fog.
 */
public final class AtmosConfigTheme {
    private AtmosConfigTheme() {}

    // Atmospheric palette
    public static final int SKY_BLUE       = 0x87CEEB;
    public static final int SUNSET_ORANGE  = 0xFF8C42;
    public static final int GOLDEN_HOUR    = 0xFFD700;
    public static final int TWILIGHT_BLUE  = 0x4A5D8A;
    public static final int NIGHT_INDIGO   = 0x2E1A47;
    public static final int FOG_GRAY       = 0xB0BEC5;
    public static final int MIST_WHITE     = 0xF5F5F5;

    // Semantic
    public static final int PRIMARY   = SKY_BLUE;
    public static final int ACCENT    = SUNSET_ORANGE;
    public static final int SUCCESS   = 0x66BB6A;
    public static final int WARNING   = 0xFFA726;
    public static final int DANGER    = 0xEF5350;

    // Text
    public static final int TEXT_PRIMARY   = 0xE0E0E0;
    public static final int TEXT_SECONDARY = 0x9E9E9E;
}