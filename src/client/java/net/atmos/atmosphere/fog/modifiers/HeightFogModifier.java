package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;

/**
 * Adjusts fog based on camera altitude.
 *
 * High altitude: fog distances expand (thinner air column) and color shifts
 * toward cold atmospheric blue. Low altitude/valley: fog compresses and
 * collects in terrain depressions.
 *
 * Tuning notes:
 *
 * Start ratio override (line: start = end * lerp(...)):
 *   Previously used 0.45 as the open-biome target ratio, which overrode
 *   desert's natural start/end ratio of ~0.18 and pushed it to 0.45 —
 *   more than doubling the near-fog zone. Combined with DryAtmosphereModifier
 *   pushing start further back, desert/savanna had fog only in the last 40%
 *   of render distance. Now uses 0.30 — still gives open biomes a wider
 *   clear zone than enclosed biomes, but doesn't destroy natural biome ratios.
 *
 * Altitude color blend cap (t * 0.55):
 *   Previously the blend reached t=1.0 at Y=280, fully replacing biome color
 *   with the cold atmospheric reference. Badlands and desert lost all their
 *   warm identity at altitude — the sky looked empty rather than atmospheric.
 *   Capping at 0.55 means the biome color is always present at 45% minimum
 *   even at extreme altitude. The cold shift still reads clearly but the
 *   biome stays recognizable.
 */
public final class HeightFogModifier implements FogModifier {

    private static final float ALTITUDE_DECAY      = 0.018f;
    private static final float ALTITUDE_MIN_RETAIN = 0.12f;

    private static final float ALTITUDE_COLOR_START = 140f;
    private static final float ALTITUDE_COLOR_FULL  = 280f;

    // Maximum blend toward cold atmospheric reference at extreme altitude.
    // 0.55 = biome color always contributes at least 45% even at Y=280+.
    // Prevents full erasure of warm biome identity (desert, badlands) at altitude.
    private static final float ALTITUDE_COLOR_MAX_BLEND = 0.55f;

    private static final float ATMO_REF_R = 0.72f;
    private static final float ATMO_REF_G = 0.76f;
    private static final float ATMO_REF_B = 0.88f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        float y = ctx.cameraY();

        float altFactor = (float) Math.exp(-(Math.max(0f, y - 64f)) * ALTITUDE_DECAY);
        altFactor = Math.max(ALTITUDE_MIN_RETAIN, altFactor);

        float end   = fog.end()   * FogMath.lerp(1.0f, 1.55f, 1.0f - altFactor);
        float start = fog.start() * FogMath.lerp(1.0f, 1.65f, 1.0f - altFactor);

        float valleyDepth    = FogMath.clamp((64f - y) / 40f, 0f, 1f);
        float valleyStrength = valleyDepth * FogMath.lerp(0.3f, 1.0f, env.humidityMass);
        end   *= FogMath.lerp(1.0f, 0.82f, valleyStrength);
        start *= FogMath.lerp(1.0f, 0.68f, valleyStrength);

        // Start ratio: lerp from the biome's natural ratio toward 0.30 for open biomes.
        // Previous value was 0.45, which crushed desert's natural 0.18 ratio and
        // made open biomes appear fog-free by pushing start too far back.
        // 0.30 still differentiates open from enclosed but preserves biome identity.
        float currentRatio = (end > 0f) ? start / end : 0.3f;
        start = end * FogMath.lerp(currentRatio, 0.30f, fog.openness());

        end   *= FogMath.lerp(1.0f, 1.12f, fog.contrastRetention());
        start *= FogMath.lerp(1.0f, 0.92f, fog.contrastRetention());

        // Gradual per-channel shift at normal altitudes.
        float heightFactor = FogMath.clamp((y - 62f) / 100f, -1f, 1f);
        float red   = FogMath.clamp(fog.red()  - 0.030f * heightFactor, 0f, 1f);
        float green = fog.green();
        float blue  = FogMath.clamp(fog.blue() + 0.045f * heightFactor, 0f, 1f);

        // High-altitude color correction: blend toward cold atmospheric reference.
        // Capped at ALTITUDE_COLOR_MAX_BLEND so biome color always contributes
        // at least (1 - max_blend) = 45% — biome stays recognizable at altitude.
        if (y > ALTITUDE_COLOR_START) {
            float t = FogMath.smoothstep(
                    FogMath.clamp((y - ALTITUDE_COLOR_START) / (ALTITUDE_COLOR_FULL - ALTITUDE_COLOR_START), 0f, 1f)
            ) * ALTITUDE_COLOR_MAX_BLEND;
            red   = FogMath.lerp(red,   ATMO_REF_R, t);
            green = FogMath.lerp(green, ATMO_REF_G, t);
            blue  = FogMath.lerp(blue,  ATMO_REF_B, t);
        }

        return fog.with(start, end,
                FogMath.clamp(red, 0f, 1f), FogMath.clamp(green, 0f, 1f), FogMath.clamp(blue, 0f, 1f));
    }
}