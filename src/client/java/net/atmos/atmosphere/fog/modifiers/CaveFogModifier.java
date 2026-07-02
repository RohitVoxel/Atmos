package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.config.AtmosConfig;

/**
 * Underground atmosphere: compression, darkness, and cool damp air.
 *
 * Activates as the camera descends below the surface threshold (Y=50).
 * Full cave atmosphere is reached at Y=20 — deep enough that the player
 * is unambiguously underground with no sky influence.
 *
 * Distance compression: caves are enclosed. Fog pools close to the camera,
 * matching how real underground spaces feel — the world ends at the torch radius.
 *
 * Color shift: cool blue-grey tint from damp stone and absent sunlight.
 * No thermal energy underground — the warmth from daylight doesn't reach here.
 * humidityMass scales the effect: a cave in a jungle biome feels wetter and
 * denser than a cave in a desert biome.
 *
 * Surface transition: the modifier fades to zero above Y=50 so cave entrances
 * feel like a gradual atmospheric shift rather than a hard cutoff.
 *
 * Pipeline position: last — runs after all surface modifiers so it can
 * override the surface atmosphere completely at depth without interference.
 *
 * Toggle: shares config.fog.fogEnabled — cave atmosphere is core fog behaviour.
 */
public final class CaveFogModifier implements FogModifier {

    // Y level where cave effect begins fading in.
    private static final float CAVE_SURFACE_THRESHOLD = 50f;

    // Y level where cave effect reaches full strength.
    private static final float CAVE_FULL_DEPTH = 20f;

    // Maximum fog end compression at full depth.
    // 0.55 = fog end reduced to 55% of surface value — the world closes in.
    private static final float MAX_END_COMPRESSION   = 0.55f;

    // Maximum fog start compression — near-fog zone tightens significantly.
    private static final float MAX_START_COMPRESSION = 0.70f;

    // Color shift magnitudes at full depth, zero humidity.
    // Cool dark tint: red and green drop, blue lifts slightly.
    private static final float CAVE_RED_DROP   = 0.055f;
    private static final float CAVE_GREEN_DROP = 0.035f;
    private static final float CAVE_BLUE_LIFT  = 0.018f;

    // Humidity amplifier: wet caves feel denser and darker.
    private static final float HUMIDITY_AMP = 0.35f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        if (!AtmosConfig.get().fog.fogEnabled) return fog;

        float y = ctx.cameraY();
        if (y >= CAVE_SURFACE_THRESHOLD) return fog;

        // Depth factor: 0 at surface threshold, 1 at full cave depth.
        float depthFactor = FogMath.smoothstep(
                FogMath.clamp((CAVE_SURFACE_THRESHOLD - y) / (CAVE_SURFACE_THRESHOLD - CAVE_FULL_DEPTH), 0f, 1f)
        );

        // Humidity amplifies the cave atmosphere — wet biome caves are denser.
        // Clamped so even arid biome caves still have a minimum cave character.
        float humidAmp  = 1f + env.humidityMass * HUMIDITY_AMP;
        float strength  = FogMath.clamp(depthFactor * humidAmp, 0f, 1f);

        if (strength < 0.01f) return fog;

        // --- Distance compression ---
        float end   = fog.end()   * FogMath.lerp(1.0f, MAX_END_COMPRESSION,   strength);
        float start = fog.start() * FogMath.lerp(1.0f, MAX_START_COMPRESSION, strength);
        start = FogMath.clamp(start, 1f, end * 0.75f);

        // --- Cool dark tint ---
        float red   = FogMath.clamp(fog.red()   - CAVE_RED_DROP   * strength, 0f, 1f);
        float green = FogMath.clamp(fog.green() - CAVE_GREEN_DROP * strength, 0f, 1f);
        float blue  = FogMath.clamp(fog.blue()  + CAVE_BLUE_LIFT  * strength, 0f, 1f);

        return fog.with(start, end, red, green, blue);
    }
}