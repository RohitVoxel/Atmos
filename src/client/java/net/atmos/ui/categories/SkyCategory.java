package net.atmos.ui.categories;

import dev.isxander.yacl3.api.ConfigCategory;
import net.atmos.config.AtmosConfig;
import net.atmos.config.SkyPhaseConfig;
import net.atmos.ui.util.CategoryHelper;
import net.minecraft.network.chat.Component;

/**
 * Sky category for YACL v3.
 */
public final class SkyCategory {

    public static ConfigCategory create(AtmosConfig config) {
        SkyPhaseConfig sky = config.skyPhase;

        return ConfigCategory.createBuilder()
                .name(Component.literal("Sky"))
                .group(CategoryHelper.group("Sky Phase System", "Core sky rendering enhancements.")
                        .option(CategoryHelper.toggle("Enhanced Sky", "Enables advanced sky phase system with interpolated colors.", true, () -> sky.enhancedSkyEnabled, v -> sky.enhancedSkyEnabled = v))
                        .option(CategoryHelper.toggle("Blue Hour", "Enables deep navy civil-twilight effect.", true, () -> sky.blueHourEnabled, v -> sky.blueHourEnabled = v))
                        .option(CategoryHelper.toggle("Sky Effects Master", "Master toggle for all sky rendering.", true, () -> config.fog.skyEnabled, v -> config.fog.skyEnabled = v))
                        .build())

                .group(CategoryHelper.group("Color & Lighting", "Adjust sky color blending and twilight vividness.")
                        .option(CategoryHelper.floatSlider("Sky Color Intensity", "How strongly Atmos sky colors blend with vanilla.", 1.0f, 0.0f, 1.5f, 0.01f, () -> sky.skyColorIntensity, v -> sky.skyColorIntensity = v, v -> String.format("%.0f%%", v * 100f)))
                        .option(CategoryHelper.floatSlider("Twilight Intensity", "Saturation of dawn and dusk colors.", 1.0f, 0.0f, 2.0f, 0.01f, () -> sky.twilightIntensity, v -> sky.twilightIntensity = v, v -> String.format("%.0f%%", v * 100f)))
                        .option(CategoryHelper.floatSlider("Night Brightness", "Minimum sky luminance during deep night.", 0.3f, 0.0f, 1.0f, 0.01f, () -> sky.nightBrightness, v -> sky.nightBrightness = v, v -> String.format("%.0f%%", v * 100f)))
                        .build())

                .group(CategoryHelper.group("Timing", "Control sky transition speed.")
                        .option(CategoryHelper.floatSlider("Transition Speed", "Multiplier for sky color transition speed.", 1.0f, 0.25f, 4.0f, 0.05f, () -> sky.transitionSpeedMultiplier, v -> sky.transitionSpeedMultiplier = v, v -> String.format("%.2fx", v)))
                        .build())
                .build();
    }
}