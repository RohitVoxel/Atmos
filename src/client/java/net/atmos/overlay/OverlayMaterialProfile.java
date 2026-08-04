package net.atmos.overlay;

import java.util.EnumMap;
import java.util.Map;

/**
 * Build/clear rate multipliers per (material, overlay type). Implementation
 * -defined relative rates — the Master Guide gives only qualitative bands
 * (Fast/Medium/Slow/Very Slow/Very Fast/High/Very Low), not numeric anchors.
 */
public final class OverlayMaterialProfile {

    private OverlayMaterialProfile() {}

    private static final float FAST = 0.045f, MEDIUM = 0.022f, SLOW = 0.010f, VERY_SLOW = 0.005f;
    private static final float VERY_FAST = 0.070f, HIGH = 0.055f, VERY_LOW = 0.004f;

    private static final Map<OverlayMaterial, Map<OverlayType, Float>> BUILD = new EnumMap<>(OverlayMaterial.class);
    private static final Map<OverlayMaterial, Map<OverlayType, Float>> CLEAR = new EnumMap<>(OverlayMaterial.class);

    static {
        rate(OverlayMaterial.ORGANIC_SOIL, OverlayType.SNOW, FAST, MEDIUM);
        rate(OverlayMaterial.ORGANIC_SOIL, OverlayType.WET, MEDIUM, MEDIUM);
        rate(OverlayMaterial.ORGANIC_SOIL, OverlayType.DUST, MEDIUM, MEDIUM);

        rate(OverlayMaterial.ROCK, OverlayType.SNOW, MEDIUM, SLOW);
        rate(OverlayMaterial.ROCK, OverlayType.WET, SLOW, SLOW);
        rate(OverlayMaterial.ROCK, OverlayType.DUST, SLOW, SLOW);

        rate(OverlayMaterial.WOOD, OverlayType.SNOW, MEDIUM, MEDIUM);
        rate(OverlayMaterial.WOOD, OverlayType.WET, FAST, MEDIUM);
        rate(OverlayMaterial.WOOD, OverlayType.DUST, MEDIUM, MEDIUM);

        rate(OverlayMaterial.METAL, OverlayType.SNOW, SLOW, MEDIUM);
        rate(OverlayMaterial.METAL, OverlayType.WET, FAST, FAST);
        rate(OverlayMaterial.METAL, OverlayType.DUST, VERY_LOW, MEDIUM);

        rate(OverlayMaterial.SAND, OverlayType.SNOW, VERY_SLOW, FAST);
        rate(OverlayMaterial.SAND, OverlayType.WET, VERY_FAST, VERY_FAST);
        rate(OverlayMaterial.SAND, OverlayType.DUST, HIGH, MEDIUM);

        rate(OverlayMaterial.SNOW, OverlayType.SNOW, FAST, MEDIUM);
        rate(OverlayMaterial.MAN_MADE, OverlayType.SNOW, MEDIUM, SLOW);
        rate(OverlayMaterial.MAN_MADE, OverlayType.WET, MEDIUM, MEDIUM);
        rate(OverlayMaterial.MAN_MADE, OverlayType.DUST, MEDIUM, MEDIUM);

        rate(OverlayMaterial.DEFAULT, OverlayType.SNOW, MEDIUM, MEDIUM);
        rate(OverlayMaterial.DEFAULT, OverlayType.WET, MEDIUM, MEDIUM);
        rate(OverlayMaterial.DEFAULT, OverlayType.DUST, MEDIUM, MEDIUM);

        rate(OverlayMaterial.METAL,        OverlayType.FROST, FAST, FAST);
        rate(OverlayMaterial.ROCK,         OverlayType.FROST, MEDIUM, SLOW);
        rate(OverlayMaterial.WOOD,         OverlayType.FROST, MEDIUM, MEDIUM);

        rate(OverlayMaterial.ORGANIC_SOIL, OverlayType.FROST, FAST, MEDIUM);
        rate(OverlayMaterial.SAND,         OverlayType.FROST, SLOW, FAST);
        rate(OverlayMaterial.DEFAULT,      OverlayType.FROST, MEDIUM, MEDIUM);

        rate(OverlayMaterial.ORGANIC_SOIL, OverlayType.POLLEN, MEDIUM, MEDIUM);
        rate(OverlayMaterial.ROCK,         OverlayType.POLLEN, SLOW, SLOW);
        rate(OverlayMaterial.WOOD,         OverlayType.POLLEN, MEDIUM, MEDIUM);

        rate(OverlayMaterial.SAND,         OverlayType.POLLEN, SLOW, FAST);
        rate(OverlayMaterial.DEFAULT,      OverlayType.POLLEN, MEDIUM, MEDIUM);
    }

    private static void rate(OverlayMaterial m, OverlayType t, float build, float clear) {
        BUILD.computeIfAbsent(m, k -> new EnumMap<>(OverlayType.class)).put(t, build);
        CLEAR.computeIfAbsent(m, k -> new EnumMap<>(OverlayType.class)).put(t, clear);
    }

    public static float buildRate(OverlayMaterial m, OverlayType t) {
        return BUILD.getOrDefault(m, Map.of()).getOrDefault(t, MEDIUM);
    }

    public static float clearRate(OverlayMaterial m, OverlayType t) {
        return CLEAR.getOrDefault(m, Map.of()).getOrDefault(t, MEDIUM);
    }
}