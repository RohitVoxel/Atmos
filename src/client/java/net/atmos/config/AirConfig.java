package net.atmos.config;

/**
 * Air Foundation (Stage 1) configuration. Governs only whether the
 * atmospheric foundation simulation runs and how quickly it responds —
 * it never configures rendering, since Air publishes state only.
 */
public final class AirConfig {

    public boolean airSimulationEnabled = true;
    public float   simulationSpeed      = 1.0f;

    public float safeSimulationSpeed() {
        return Math.clamp(simulationSpeed, 0.1f, 5.0f);
    }
}