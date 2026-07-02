package net.atmos.atmosphere.sky;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;

/**
 * Reduces sky brightness when dense fog is present — the sun-glare-through-
 * fog effect where heavy overcast or fog washes out the sky's apparent brightness.
 *
 * DENSE_FOG_THRESHOLD: fog end below this value triggers the density factor.
 *
 * Weather gate (new):
 * Previously, densityFactor fired based on fogEnd alone. ValleyFogModifier
 * compresses fog end below 80 blocks in clear weather at low Y positions —
 * a valley depression in full sunlight would trigger sky darkening even with
 * zero storm activity. This is physically wrong: valley fog is a ground-level
 * phenomenon, not an overcast condition. The sky above a misty valley is still
 * clear and bright.
 *
 * densityFactor is now weighted by stormEnergy before contributing to glare.
 * At zero storm energy, dense fog end from valley/geometry compression produces
 * zero glare contribution. At full storm energy, the path fires at full strength.
 * The stormEnergy * 0.55f direct contribution is unchanged — thunder-driven
 * glare still fires even when fog end is wide.
 *
 * Effect on existing behavior:
 *   Clear weather valley:    densityFactor non-zero, weatherGate=0 → glare=0 ✓
 *   Storm with wide fog:     densityFactor=0, stormEnergy term active → glare fires ✓
 *   Storm with dense fog:    both terms active → full glare as before ✓
 *   Mild overcast, clear fog: partial glare from stormEnergy term only → correct ✓
 */
public final class SunGlareController {

    private static final float DENSE_FOG_THRESHOLD = 80f;

    public int apply(int skyColor, FogContext ctx, EnvironmentalState env, float fogEnd) {
        float dayFactor = Math.max(0f, (float) Math.cos(ctx.sunAngle()));
        if (dayFactor <= 0f) return skyColor;

        float stormEnergy   = env.getStormEnergy();
        float densityFactor = FogMath.clamp(1f - (fogEnd / DENSE_FOG_THRESHOLD), 0f, 1f);

        // Weather gate: densityFactor only contributes to glare when storm
        // energy is present. Without this, valley fog compression (which drives
        // fogEnd below 80 in clear weather) darkens the sky spuriously.
        // smoothstep gives a gradual onset — a light overcast barely gates the
        // density contribution, a heavy storm lets it through fully.
        float weatherGate   = FogMath.smoothstep(FogMath.clamp(stormEnergy * 2.5f, 0f, 1f));
        float glare         = FogMath.clamp(densityFactor * weatherGate + stormEnergy * 0.55f, 0f, 1f) * dayFactor;

        if (glare <= 0f) return skyColor;

        float reduction = 1f - (0.08f * glare);
        float r = ((skyColor >> 16) & 0xFF) / 255f * reduction;
        float g = ((skyColor >>  8) & 0xFF) / 255f * reduction;
        float b = ( skyColor        & 0xFF) / 255f * reduction;

        return packRGB(FogMath.clamp(r, 0f, 1f), FogMath.clamp(g, 0f, 1f), FogMath.clamp(b, 0f, 1f));
    }

    private static int packRGB(float r, float g, float b) {
        return ((int)(r * 255f) << 16) | ((int)(g * 255f) << 8) | (int)(b * 255f);
    }
}