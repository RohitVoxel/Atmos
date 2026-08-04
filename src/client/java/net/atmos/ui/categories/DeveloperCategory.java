package net.atmos.ui.categories;

import dev.isxander.yacl3.api.ConfigCategory;
import net.atmos.config.AtmosConfig;
import net.atmos.config.DebugConfig;
import net.atmos.ui.util.CategoryHelper;
import net.minecraft.network.chat.Component;

/**
 * Developer category.
 */
public final class DeveloperCategory {

    public static ConfigCategory create(AtmosConfig config) {
        DebugConfig debug = config.debug;

        return ConfigCategory.createBuilder()
                .name(Component.literal("Developer"))
                .group(CategoryHelper.group("Diagnostics", "Runtime diagnostic overlays and logging options.")
                        .option(CategoryHelper.toggle("Debug Overlay", "Enables the in-game diagnostic overlay (F8) showing atmospheric state and pipeline metrics.", false, () -> debug.overlayEnabled, v -> debug.overlayEnabled = v))
                        .option(CategoryHelper.toggle("Log Biome Changes", "Prints biome transition events to the game log for debugging fog blending.", false, () -> debug.logBiomeChanges, v -> debug.logBiomeChanges = v))
                        .option(CategoryHelper.toggle("Log Modifier Values", "Prints per-frame fog modifier calculations to the game log.", false, () -> debug.logModifierValues, v -> debug.logModifierValues = v))
                        .build())
                .group(CategoryHelper.group("Development", "Workflow helpers for pack developers and contributors.")
                        .option(CategoryHelper.toggle("Auto Config Reload", "Automatically reloads configuration when atmos.json is modified on disk. Useful for rapid iteration without restarting the game.", false, () -> debug.configAutoReload, v -> debug.configAutoReload = v))
                        .build())
                .build();
    }
}