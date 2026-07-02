package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;
import net.atmos.config.AtmosConfig;

/**
 * Gives dry, open biomes atmospheric identity without adding fog.
 *
 * Dry air isn't invisible — but the *kind* of dryness differs by biome:
 *
 *   Desert:   bleached, pale, thermally hazy. Horizon trends toward warm white.
 *             The sky erases into whitish distance. High red AND green lift,
 *             strong blue drop — yellow-white bleach effect.
 *
 *   Savanna:  golden, radiant, warm grass light. The horizon glows warm amber
 *             without turning dusty-red. Moderate red lift, slight green lift,
 *             mild blue drop — straw-gold warmth.
 *
 *   Badlands: mineral dust, red-iron atmosphere. Horizon has orange-red cast.
 *             Strong red-amber push, no green lift, minimal blue drop — the
 *             dust is red, not yellow.
 *
 * The biome's fog color encodes this: we derive "dust warmth" and "dust redness"
 * from the fog profile's R/G/B balance. No new fields required.
 *
 *   fog.red() - fog.blue()  →  overall warmth    (desert: 0.32, badlands: 0.42, savanna: 0.32)
 *   fog.red() - fog.green() →  redness vs yellow (desert: 0.09, badlands: 0.28, savanna: 0.10)
 *
 * This means:
 *   - Badlands has high redness (0.28) → get a red-dominant push
 *   - Desert has low redness (0.09) → get a yellow-white push (red+green, strong blue drop)
 *   - Savanna has low redness (0.10) → get a golden-warm push (moderate, soft)
 *
 * Only activates in: low humidity + thermal energy present + open biome.
 * No effect at night, in storms, or in humid biomes.
 *
 * Toggle: config.fog.dryAtmosphere
 */
public final class DryAtmosphereModifier implements FogModifier {

    // Base distance push — same for all dry biomes.
    // HeightFogModifier already widens the clear zone; this adds the final
    // "airy, exposed" quality on top.
    private static final float DRY_START_PUSH = 0.15f;
    private static final float DRY_END_PUSH   = 0.08f;

    // Shared base: all dry biomes get a slight warm shift.
    private static final float BASE_RED_LIFT   = 0.020f;
    private static final float BASE_BLUE_DROP  = 0.018f;

    // Desert bleach bonus: push toward yellow-white (high red+green, strong blue drop).
    // Fires when fog.red() - fog.green() is LOW (yellow-dominant, not red-dominant).
    private static final float BLEACH_RED_BONUS   = 0.025f;
    private static final float BLEACH_GREEN_BONUS = 0.015f;
    private static final float BLEACH_BLUE_EXTRA  = 0.018f;

    // Badlands mineral dust bonus: push toward red-orange (high red, suppress green).
    // Fires when fog.red() - fog.green() is HIGH (red-dominant).
    private static final float DUST_RED_BONUS    = 0.030f;
    private static final float DUST_GREEN_REDUCE = 0.012f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        if (!AtmosConfig.get().fog.dryAtmosphere) return fog;

        float humidity = env.humidityMass;
        float thermal  = env.thermalEnergy;
        float openness = fog.openness();

        float dryness     = 1f - humidity;
        float dryStrength = dryness * thermal * openness;

        if (dryStrength < 0.05f) return fog;

        float t = FogMath.smoothstep(FogMath.clamp((dryStrength - 0.05f) / 0.6f, 0f, 1f));

        // Distance push — same for all dry open biomes.
        float start = fog.start() * (1f + DRY_START_PUSH * t);
        float end   = fog.end()   * (1f + DRY_END_PUSH   * t);
        start = Math.min(start, end * 0.80f);

        // Base warm shift applied to all dry biomes.
        float red   = fog.red()   + BASE_RED_LIFT  * t;
        float green = fog.green();
        float blue  = fog.blue()  - BASE_BLUE_DROP * t;

        // Derive dust character from fog profile color balance.
        // redness = how red-dominant vs yellow: high → badlands-like mineral dust
        //                                       low  → desert/savanna bleach or golden
        float redness = fog.red() - fog.green();  // badlands: ~0.28, desert: ~0.09, savanna: ~0.10

        if (redness > 0.18f) {
            // Badlands: red-iron mineral dust. Push toward orange-red.
            // Scale the bonus by how much redness exceeds the threshold.
            float dustStrength = FogMath.clamp((redness - 0.18f) / 0.20f, 0f, 1f);
            red   += DUST_RED_BONUS    * t * dustStrength;
            green -= DUST_GREEN_REDUCE * t * dustStrength;
        } else {
            // Desert or savanna: bleach/golden warmth.
            // Desert has lower humidity (0.05) than savanna (0.18), so its dryStrength
            // and t value will be higher at the same thermal level — it naturally gets
            // a stronger effect. No extra branching needed; the magnitude handles it.
            red   += BLEACH_RED_BONUS   * t;
            green += BLEACH_GREEN_BONUS * t;
            blue  -= BLEACH_BLUE_EXTRA  * t;
        }

        return fog.with(start, end,
                FogMath.clamp(red, 0f, 1f), FogMath.clamp(green, 0f, 1f), FogMath.clamp(blue, 0f, 1f));
    }
}