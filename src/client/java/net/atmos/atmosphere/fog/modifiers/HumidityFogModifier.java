package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;

public final class HumidityFogModifier implements FogModifier {

    private static final float SOFTNESS_STRENGTH = 0.24f;
    private static final float RAIN_AMPLIFY      = 0.14f;
    private static final float THERMAL_LIFT      = 0.10f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        float humidity = env.humidityMass;
        float thermal  = env.thermalEnergy;

        // Thermal counteracts humidity softness: hot air disperses ground haze.
        float softnessBias  = (humidity - 0.5f) * SOFTNESS_STRENGTH;
        float thermalOffset = thermal * THERMAL_LIFT * humidity;
        float start = fog.start() * (1f - (softnessBias - thermalOffset));
        float end   = fog.end();

        start = FogMath.clamp(start, 1f, end * 0.75f);

        if (ctx.rain() > 0f && humidity > 0.5f) {
            float humidExcess   = (humidity - 0.5f) * 2f;
            float rainAmplifier = ctx.rain() * humidExcess * RAIN_AMPLIFY * (1f - thermal * 0.3f);
            end   *= (1f - rainAmplifier);
            start *= (1f - rainAmplifier * 0.5f);
        }

        return fog.withDistances(start, end);
    }
}