package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.config.AtmosConfig;

/**
 * Low-altitude mist layer in humid biomes at dawn and night.
 * Pulls fog start closer and adds a slight blue-white brightening when
 * the camera is below Y=72 in high-humidity conditions.
 *
 * Only fires at dawn/dusk windows and nighttime — midday thermal energy
 * disperses ground mist, so the modifier is time-gated.
 *
 * Toggle: config.fog.fogEnabled (uses master fog toggle — no separate
 * config key since this is a core fog behaviour, not an optional system).
 */
public final class AtmosphericLayerModifier implements FogModifier {

    private static final float MIST_CEILING   = 72f;
    private static final float MIST_PULL      = 0.35f;
    private static final float MIST_BLUE_LIFT = 0.04f;
    private static final float MIST_BRIGHT    = 0.03f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        // Shares the master fog toggle — atmospheric layer is core fog behaviour.
        if (!AtmosConfig.get().fog.fogEnabled) return fog;

        float y        = ctx.cameraY();
        float humidity = env.humidityMass;

        if (y >= MIST_CEILING || humidity < 0.25f) return fog;

        float depthFactor = FogMath.smoothstep(FogMath.clamp((MIST_CEILING - y) / MIST_CEILING, 0f, 1f));
        float humidFactor = FogMath.clamp((humidity - 0.25f) / 0.75f, 0f, 1f);

        float sunHeight  = (float) Math.cos(ctx.sunAngle());
        float dawnWindow = FogMath.clamp(1f - (sunHeight + 0.1f) / 0.6f, 0f, 1f);
        float nightMist  = Math.max(0f, -sunHeight) * 0.4f;
        float timeFactor = Math.max(dawnWindow, nightMist);

        float strength = depthFactor * humidFactor * timeFactor;
        if (strength < 0.01f) return fog;

        float start = FogMath.clamp(fog.start() * (1f - MIST_PULL * strength), 1f, fog.end() * 0.6f);
        float red   = FogMath.clamp(fog.red()   + MIST_BRIGHT    * strength, 0f, 1f);
        float green = FogMath.clamp(fog.green() + MIST_BRIGHT    * strength, 0f, 1f);
        float blue  = FogMath.clamp(fog.blue()  + MIST_BLUE_LIFT * strength, 0f, 1f);

        return fog.with(start, fog.end(), red, green, blue);
    }
}