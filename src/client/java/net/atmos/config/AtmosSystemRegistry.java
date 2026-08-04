package net.atmos.config;

import net.atmos.atmosphere.fog.FogManager;
import net.atmos.atmosphere.sky.SkyPhaseController;

/** Single source of truth for every AtmosReloadable system. Add new systems only here. */
public final class AtmosSystemRegistry {

    private AtmosSystemRegistry() {}

    public static void registerAll(FogManager fogManager, SkyPhaseController skyPhaseController) {
        AtmosReloadManager.register(fogManager);
        AtmosReloadManager.register(skyPhaseController);

        // Future systems — add one line each, nothing else changes:
        // AtmosReloadManager.register(cloudSystem);
        // AtmosReloadManager.register(windSystem);
        // AtmosReloadManager.register(lightingSystem);
    }
}