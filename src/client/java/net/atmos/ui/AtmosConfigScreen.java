package net.atmos.ui;

import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.atmos.config.AtmosConfig;
import net.atmos.ui.categories.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main Atmos configuration screen factory.
 * Uses standard YACL v3 API.
 */
public final class AtmosConfigScreen {

    public static Screen create(Screen parent) {
        AtmosConfig config = AtmosConfig.get();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Atmos"))
                .category(GeneralCategory.create(config))
                .category(SkyCategory.create(config))
                .category(FogCategory.create(config))
                .category(DeveloperCategory.create(config))
                .save(AtmosConfig::save)
                .build()
                .generateScreen(parent);
    }
}