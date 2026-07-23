package net.atmos.render;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Definition Producer — Appendix ZB Blocker 3.
 *
 * humidityMass/thermalEnergy are already clamped to [0,1] by
 * EnvironmentalState.advance() — not re-clamped here; only the final
 * composed scalar is clamped, per Blocker 3's equation. Coefficients
 * sourced from RenderingMathConstants (Appendix ZB §III).
 *
 * Stateless, deterministic, O(1).
 */
public final class DefinitionProducer {

    private DefinitionProducer() {}

    public static DefinitionResult evaluate(EnvironmentalState env) {
        float humidity = env.humidityMass;
        float thermal = env.thermalEnergy;

        float raw = 1.0f
                - (humidity * RenderingMathConstants.DEFINITION_HUMIDITY_SCALAR)
                + (thermal * RenderingMathConstants.DEFINITION_THERMAL_SCALAR);

        return new DefinitionResult(FogMath.clamp(raw, 0.1f, 1.0f));
    }
}