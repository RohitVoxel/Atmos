package net.atmos.core;

import net.atmos.atmosphere.fog.FogManager;
import net.fabricmc.api.ClientModInitializer;

public class AtmosClient implements ClientModInitializer {

	private static final FogManager FOG_MANAGER = new FogManager();

	public static FogManager getFogManager() {
		return FOG_MANAGER;
	}

	@Override
	public void onInitializeClient() {
		// Client init — nothing needed yet.
		// Future: register keybinds, config, event listeners.
	}
}