package net.atmos.overlay;

/**
 * Publishes the Wet overlay's rain-driven contribution. Never simulates
 * rain, humidity, or thermal energy itself — consumes already-owned
 * EnvironmentalState/FogContext values supplied by the caller.
 */
public final class OverlayRainPublisher {

    private static final float RAIN_WET_WEIGHT     = 1.00f;
    private static final float HUMIDITY_WET_WEIGHT = 0.35f;
    private static final float THERMAL_DRY_WEIGHT  = 0.30f;

    private OverlayRainPublisher() {}

    public static void publish(OverlayManager overlayManager, float rainLevel, float humidityMass, float thermalEnergy) {
        float wetTarget = rainLevel * RAIN_WET_WEIGHT
                + humidityMass * HUMIDITY_WET_WEIGHT
                - thermalEnergy * THERMAL_DRY_WEIGHT;

        overlayManager.setContribution(OverlayType.WET, OverlaySource.RAIN, Math.max(0f, wetTarget));
    }
}
