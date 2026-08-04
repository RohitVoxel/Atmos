package net.atmos.ui.categories;

import dev.isxander.yacl3.api.ConfigCategory;
import net.atmos.config.AtmosConfig;
import net.atmos.config.FogConfig;
import net.atmos.ui.util.CategoryHelper;
import net.minecraft.network.chat.Component;

/**
 * Fog & Atmosphere category for YACL v3.
 */
public final class FogCategory {

    public static ConfigCategory create(AtmosConfig config) {
        FogConfig fog = config.fog;

        return ConfigCategory.createBuilder()
                .name(Component.literal("Fog & Atmosphere"))
                .group(CategoryHelper.group("Global Fog", "Master fog density and transition controls.")
                        .option(CategoryHelper.toggle("Fog Enabled", "Master toggle for all atmospheric fog.", true, () -> fog.fogEnabled, v -> fog.fogEnabled = v))
                        .option(CategoryHelper.floatSlider("Fog Intensity", "Overall fog density multiplier.", 1.0f, 0.1f, 3.0f, 0.01f, () -> fog.fogIntensity, v -> fog.fogIntensity = v, v -> String.format("%.2fx", v)))
                        .option(CategoryHelper.floatSlider("Transition Speed", "How quickly fog blends when conditions change.", 1.0f, 0.1f, 5.0f, 0.05f, () -> fog.transitionSpeed, v -> fog.transitionSpeed = v, v -> String.format("%.2fx", v)))
                        .build())

                .group(CategoryHelper.group("Weather & Night", "Fog behavior during storms and nighttime.")
                        .option(CategoryHelper.toggle("Weather Effects", "Enables storm compression, rain brightening, thunder shifts.", true, () -> fog.weatherEffects, v -> fog.weatherEffects = v))
                        .option(CategoryHelper.floatSlider("Weather Intensity", "Multiplier for weather-driven fog changes.", 1.0f, 0.0f, 2.0f, 0.01f, () -> fog.weatherIntensity, v -> fog.weatherIntensity = v, v -> String.format("%.0f%%", v * 100f)))
                        .option(CategoryHelper.toggle("Night Compression", "Enables fog enclosure during nighttime.", true, () -> fog.nightCompression, v -> fog.nightCompression = v))
                        .option(CategoryHelper.floatSlider("Night Fog Strength", "Intensity of night fog compression.", 1.0f, 0.0f, 2.0f, 0.01f, () -> fog.nightFogStrength, v -> fog.nightFogStrength = v, v -> String.format("%.0f%%", v * 100f)))
                        .build())

                .group(CategoryHelper.group("Visibility Safety Floor", "Prevents fog from compressing below safe minimums.")
                        .option(CategoryHelper.toggle("Enable Safety Floor", "Ensures fog distance never drops below safe minimum.", true, () -> fog.visibilityFloorEnabled, v -> fog.visibilityFloorEnabled = v))
                        .option(CategoryHelper.floatSlider("Floor Fraction", "Minimum fog distance as percentage of biome's clear-weather end.", 0.45f, 0.10f, 0.80f, 0.01f, () -> fog.visibilityFloorFraction, v -> fog.visibilityFloorFraction = v, v -> String.format("%.0f%%", v * 100f)))
                        .option(CategoryHelper.floatSlider("Floor Absolute", "Hard minimum fog distance in blocks.", 24.0f, 8.0f, 64.0f, 1.0f, () -> fog.visibilityFloorAbsolute, v -> fog.visibilityFloorAbsolute = v, v -> String.format("%.0f blocks", v)))
                        .build())

                .group(CategoryHelper.group("Atmospheric Features", "Specialized fog modifiers for biomes, valleys, and light rays.")
                        .option(CategoryHelper.toggle("Biome Fog Blending", "Smoothly interpolates fog when moving between biomes.", true, () -> fog.biomeFogBlending, v -> fog.biomeFogBlending = v))
                        .option(CategoryHelper.toggle("Valley Fog", "Enables fog pooling in terrain depressions.", true, () -> fog.valleyFog, v -> fog.valleyFog = v))
                        .option(CategoryHelper.toggle("Dry Atmosphere", "Enables warm, dusty tinting in arid biomes.", true, () -> fog.dryAtmosphere, v -> fog.dryAtmosphere = v))
                        .option(CategoryHelper.toggle("Crepuscular Rays", "Enables god rays during dawn and dusk.", true, () -> fog.crepuscularRays, v -> fog.crepuscularRays = v))
                        .build())
                .build();
    }
}