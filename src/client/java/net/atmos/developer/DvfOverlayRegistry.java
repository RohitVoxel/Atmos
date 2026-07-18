package net.atmos.developer;

import net.atmos.developer.overlays.*;

import java.util.EnumMap;
import java.util.Map;

public final class DvfOverlayRegistry {

    private DvfOverlayRegistry() {}

    public static Map<DvfMode, DvfOverlay> createDefaultRegistry() {
        Map<DvfMode, DvfOverlay> registry = new EnumMap<>(DvfMode.class);
        registry.put(DvfMode.ENVIRONMENTAL_STATE, new EnvironmentalOverlay());
        registry.put(DvfMode.CELL_GRID, new CellGridOverlay());
        registry.put(DvfMode.ATMOSPHERIC_MEMORY, new AtmosphericMemoryOverlay());
        registry.put(DvfMode.EXPOSURE, new ExposureOverlay());
        registry.put(DvfMode.SKY_EXPOSURE, new SkyExposureOverlay());
        return registry;
    }
}