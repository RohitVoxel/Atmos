package net.atmos.ui.categories;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.atmos.config.AtmosConfig;
import net.atmos.config.DebugConfig;
import net.atmos.config.FogConfig;
import net.atmos.config.SkyPhaseConfig;
import net.atmos.ui.AtmosConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * General / Home category.
 */
public final class GeneralCategory {

    public static ConfigCategory create(AtmosConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("General"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Reset to Defaults"))
                        .description(OptionDescription.of(Component.literal("Restores every Atmos setting to factory defaults.")))
                        .binding(false, () -> false, v -> {
                            if (v) {
                                config.fog = new FogConfig();
                                config.debug = new DebugConfig();
                                config.skyPhase = new SkyPhaseConfig();
                                AtmosConfig.save();
                                Minecraft.getInstance().execute(() ->
                                        Minecraft.getInstance().setScreen(AtmosConfigScreen.create(null)));
                            }
                        })
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Reload from Disk"))
                        .description(OptionDescription.of(Component.literal("Reloads atmos.json from disk.")))
                        .binding(false, () -> false, v -> {
                            if (v) net.atmos.config.AtmosReloadManager.reloadAll();
                        })
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .build();
    }
}