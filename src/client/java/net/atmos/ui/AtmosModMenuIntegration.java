package net.atmos.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu integration for Atmos.
 * Provides the configuration screen entry point in the mod list.
 */
public final class AtmosModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // This will compile perfectly once Loom remaps ModMenu to Mojmaps
        return AtmosConfigScreen::create;
    }
}