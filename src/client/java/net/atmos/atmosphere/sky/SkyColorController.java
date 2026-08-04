package net.atmos.atmosphere.sky;

import net.atmos.atmosphere.AtmosphereDrifter;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.config.AtmosConfig;

/**
 * Modifies the vanilla sky color to reflect atmospheric conditions.
 *
 * Reads EnvironmentalState — the same drifting values the fog system uses.
 * This ensures sky and fog are always expressing the same atmospheric moment.
 *
 * Time-of-day tinting: dawn warmth, noon brightness, dusk orange, blue hour.
 * Humidity haze: upper sky warms and desaturates in moist air.
 * Dry air identity: zenith deepens and brightens, horizon bleaches slightly.
 * Storm: sky desaturates and darkens toward grey.
 * Post-storm clearing: brief warm-white brightening as storm energy dissipates.
 * Blue hour: deep navy window between dusk and full night.
 * Starfield visibility: clear dry nights deepen the sky; humid nights haze it.
 *
 * Starfield visibility:
 * On clear dry nights, all channels darken significantly so stars read as
 * brighter against the deeper background. Blue channel drops only marginally
 * less than red/green — the sky deepens to dark navy rather than lifting
 * toward bright blue. Net blue delta on a clear night is negative: the sky
 * gets darker, not bluer. On humid nights a warm grey-blue wash fades stars.
 *
 * Blue hour:
 * Real civil twilight darkens before it shifts color. The sky loses luminance
 * across all channels, with blue dropping least — producing deep navy as a
 * consequence of dimming rather than an additive cobalt push.
 *
 * Night blue lift removed:
 * The previous dusk line included `+ 0.018f * (1f - dayFactor)` which added
 * 0.018 to blue every frame at full night (dayFactor=0). This was the primary
 * cause of the persistent bright-blue night sky. Removed entirely — blue hour
 * and the starfield system handle the night transition correctly without it.
 *
 * horizonMask uses Math.abs(sunHeight) — prevents night amplification of
 * dusk/dawn tints that caused the blue/orange/purple midnight cycling artifact.
 *
 * Per-frame advance guard:
 * getSkyColor() fires multiple times per frame. advancedThisFrame ensures
 * drifters advance exactly once per frame regardless of call count or
 * frame duration. beginFrame() must be called once per frame at render start.
 */
public final class SkyColorController {

    private final AtmosphereDrifter driftR = new AtmosphereDrifter(0.45f, 2.5f, 6.0f);
    private final AtmosphereDrifter driftG = new AtmosphereDrifter(0.55f, 2.5f, 6.0f);
    private final AtmosphereDrifter driftB = new AtmosphereDrifter(0.80f, 2.5f, 6.0f);

    private boolean firstFrame        = true;
    private boolean advancedThisFrame = false;

    private static final float MAX_DAYTIME_RB_RATIO       = 1.85f;
    private static final float SUN_HEIGHT_GUARD_THRESHOLD = 0.15f;

    // Blue hour: civil twilight window between dusk and full night.
    // Darkens before shifting hue — deep navy as a consequence of dimming.
    private static final float BLUE_HOUR_PEAK      = -0.15f;
    private static final float BLUE_HOUR_RANGE     =  0.22f;
    private static final float BLUE_HOUR_BLUE_LIFT = 0.028f;
    private static final float BLUE_HOUR_RED_DROP  = 0.018f;
    private static final float BLUE_HOUR_GREEN_DROP= 0.008f;
    private static final float BLUE_HOUR_DIM       = 0.020f;

    // Starfield visibility.
    // DARKEN raised to 0.055 — meaningfully darkens the sky so stars read.
    // BLUE_LIFT reduced to 0.005 — net blue delta is now negative on clear
    // nights (b += 0.005 - 0.055*0.3 = -0.0115), preventing sky brightening.
    private static final float STAR_CLEAR_THRESHOLD = 0.30f;
    private static final float STAR_HAZY_THRESHOLD  = 0.55f;

    private static final float STAR_CLEAR_DARKEN    = 0.055f;
    private static final float STAR_CLEAR_BLUE_LIFT = 0.005f;

    private static final float STAR_HAZY_LIFT = 0.022f;
    private static final float STAR_HAZY_WARM = 0.008f;

    // Post-storm clearing sky brightening.
    // Storm-gate: clearing only fires once stormEnergy has dropped below this.
    // Same threshold WeatherFogModifier uses for its fog clearing expansion —
    // both systems fire on the same stormClearing signal.
    private static final float CLEARING_STORM_GATE   = 0.25f;
    private static final float CLEARING_SIGNAL_FLOOR = 0.03f;
    private static final float CLEARING_SIGNAL_RANGE = 0.40f;

    /**
     * Called once per frame before any getSkyColor() calls.
     * Resets the per-frame advance guard so drifters advance exactly once.
     */
    public void beginFrame() {
        advancedThisFrame = false;
    }

    public int apply(int vanillaSkyColor, FogContext ctx, EnvironmentalState env, float deltaSec) {
        float angle       = ctx.sunAngle();
        float sunHeight   = (float) Math.cos(angle);
        float dayFactor   = Math.max(0f, sunHeight);
        float noonFactor  = dayFactor * dayFactor;
        float sinAngle    = (float) Math.sin(angle);
        float horizonMask = FogMath.clamp(1f - Math.abs(sunHeight) * 3f, 0f, 1f);
        float dawnFactor  = Math.max(0f,  sinAngle) * horizonMask;
        float duskFactor  = Math.max(0f, -sinAngle) * horizonMask;

        float r = ((vanillaSkyColor >> 16) & 0xFF) / 255f;
        float g = ((vanillaSkyColor >>  8) & 0xFF) / 255f;
        float b = ( vanillaSkyColor        & 0xFF) / 255f;

        // --- Time-of-day tinting ---
        r = Math.min(1f, r + 0.055f * dawnFactor);
        g = Math.min(1f, g + 0.018f * dawnFactor);
        b = Math.max(0f, b - 0.020f * dawnFactor);

        r = Math.min(1f, r + 0.012f * noonFactor);
        b = Math.max(0f, b - 0.022f * noonFactor);

        r = Math.min(1f, r + 0.048f * duskFactor);
        g = Math.min(1f, g + 0.012f * duskFactor);
        // Note: removed `+ 0.018f * (1f - dayFactor)` — that term added 0.018
        // to blue every frame at full night (dayFactor=0), which was the primary
        // driver of the persistent bright-blue night sky. Blue hour and the
        // starfield system handle the night transition without needing it.
        b = Math.max(0f, b - 0.025f * duskFactor);

        // --- Blue hour ---
        // Civil twilight: sky loses luminance before it shifts hue.
        // All channels dim; blue dims at half rate — deep navy emerges as a
        // consequence of darkening, not from an additive cobalt push.
        // Storm-gated: overcast twilight stays grey, not blue.
        if (AtmosConfig.get().skyPhase.blueHourEnabled && sunHeight < 0f) {
            float distFromPeak = Math.abs(sunHeight - BLUE_HOUR_PEAK);
            float rawFactor    = FogMath.clamp(1f - distFromPeak / BLUE_HOUR_RANGE, 0f, 1f);
            float blueHour     = FogMath.smoothstep(rawFactor);
            float clearSky     = FogMath.clamp(1f - env.getStormEnergy() * 2.0f, 0f, 1f);
            blueHour *= clearSky;

            if (blueHour > 0.001f) {
                float dim = BLUE_HOUR_DIM * blueHour;
                r = Math.max(0f, r - BLUE_HOUR_RED_DROP    * blueHour - dim);
                g = Math.max(0f, g - BLUE_HOUR_GREEN_DROP  * blueHour - dim);
                b = Math.min(1f, Math.max(0f, b + BLUE_HOUR_BLUE_LIFT * blueHour - dim * 0.5f));
            }
        }

        // --- Humidity haze ---
        float skyMoisture = env.getSkyMoisture();
        if (skyMoisture > 0.4f) {
            float hazeStr = (skyMoisture - 0.4f) / 0.6f;
            float gray    = r * 0.299f + g * 0.587f + b * 0.114f;
            r = FogMath.lerp(r, gray * 1.04f, hazeStr * 0.12f);
            g = FogMath.lerp(g, gray * 1.02f, hazeStr * 0.08f);
            b = FogMath.lerp(b, gray * 0.96f, hazeStr * 0.10f);
        }

        // --- Dry air identity ---
        float humidity    = env.humidityMass;
        float thermal     = env.thermalEnergy;
        float dryness     = 1f - humidity;
        float dryStrength = dryness * thermal * dayFactor;

        if (dryStrength > 0.1f) {
            float t = FogMath.smoothstep(FogMath.clamp((dryStrength - 0.1f) / 0.7f, 0f, 1f));
            b = FogMath.clamp(b + 0.030f * t, 0f, 1f);
            r = FogMath.clamp(r + 0.012f * t, 0f, 1f);
            g = FogMath.clamp(g + 0.008f * t, 0f, 1f);
        }

        // --- Storm ---
        float stormEnergy = env.getStormEnergy();
        if (stormEnergy > 0f) {
            float gray = r * 0.299f + g * 0.587f + b * 0.114f;
            r = FogMath.lerp(r, gray * 0.82f, stormEnergy * 0.38f);
            g = FogMath.lerp(g, gray * 0.82f, stormEnergy * 0.38f);
            b = FogMath.lerp(b, gray * 0.88f, stormEnergy * 0.38f);
        }


        float stormClearing = env.getStormClearing();
        if (stormClearing > CLEARING_SIGNAL_FLOOR
                && stormEnergy < CLEARING_STORM_GATE
                && dayFactor > 0f) {
            float clearStr = FogMath.smoothstep(
                    FogMath.clamp((stormClearing - CLEARING_SIGNAL_FLOOR)
                            / CLEARING_SIGNAL_RANGE, 0f, 1f));
            float clearScaled = clearStr * dayFactor;
            // Warm-white brightness lift: red leads, green follows, blue minimal.
            // The warmth reads as direct sunlight rather than cold post-rain haze.
            r = Math.min(1f, r + 0.030f * clearScaled);
            g = Math.min(1f, g + 0.018f * clearScaled);
            b = Math.min(1f, b + 0.008f * clearScaled);
        }

        // --- Starfield visibility ---
        // Clear nights: all channels darken significantly. Net blue is negative —
        // the sky deepens to dark navy rather than lifting toward bright blue.
        // Humid nights: warm grey-blue wash makes stars appear faded.
        float nightDepth = env.getNightDepth();
        if (nightDepth > 0.3f && stormEnergy < 0.5f) {
            float nightScale = FogMath.smoothstep(
                    FogMath.clamp((nightDepth - 0.3f) / 0.7f, 0f, 1f));
            float stormGate  = FogMath.clamp(1f - stormEnergy * 2.0f, 0f, 1f);

            if (skyMoisture < STAR_CLEAR_THRESHOLD) {
                float clearness = FogMath.smoothstep(
                        FogMath.clamp(1f - skyMoisture / STAR_CLEAR_THRESHOLD, 0f, 1f));
                float clearStr  = clearness * nightScale * stormGate;

                r = Math.max(0f, r - STAR_CLEAR_DARKEN    * clearStr);
                g = Math.max(0f, g - STAR_CLEAR_DARKEN    * clearStr);
                // Net blue: +0.005 - 0.055*0.3 = -0.0115 at full clearness.
                // Sky darkens across all channels; blue drops least — dark navy.
                b = Math.min(1f, Math.max(0f, b + STAR_CLEAR_BLUE_LIFT * clearStr
                        - STAR_CLEAR_DARKEN * clearStr * 0.3f));

            } else if (skyMoisture > STAR_HAZY_THRESHOLD) {
                float haziness = FogMath.smoothstep(
                        FogMath.clamp(
                                (skyMoisture - STAR_HAZY_THRESHOLD) / (1f - STAR_HAZY_THRESHOLD),
                                0f, 1f));
                float hazyStr  = haziness * nightScale * stormGate;

                r = Math.min(1f, r + STAR_HAZY_WARM * hazyStr);
                g = Math.min(1f, g + STAR_HAZY_WARM * hazyStr * 0.6f);
                b = Math.min(1f, b + STAR_HAZY_LIFT * hazyStr);
            }
        }

        // --- Thunder flash ---
        float thunderFlash = env.getThunderFlash();
        if (thunderFlash > 0.001f) {
            float flashStr = FogMath.smoothstep(thunderFlash)
                    * FogMath.lerp(0.35f, 1.0f, env.getNightDepth());
            r = Math.min(1f, r + 0.30f * flashStr);
            g = Math.min(1f, g + 0.30f * flashStr);
            b = Math.min(1f, b + 0.28f * flashStr);
        }

        // --- r/b ratio guard ---
        if (sunHeight > SUN_HEIGHT_GUARD_THRESHOLD && b > 0.01f) {
            float rb = r / b;
            if (rb > MAX_DAYTIME_RB_RATIO) {
                float scale = (MAX_DAYTIME_RB_RATIO * b) / r;
                r *= scale;
                g *= scale;
            }
        }

        // --- Output smoothing with per-frame advance guard ---
        if (firstFrame) {
            driftR.snap(r);
            driftG.snap(g);
            driftB.snap(b);
            firstFrame        = false;
            advancedThisFrame = true;
        }

        float smoothR, smoothG, smoothB;

        if (!advancedThisFrame) {
            smoothR = driftR.advance(r, deltaSec);
            smoothG = driftG.advance(g, deltaSec);
            smoothB = driftB.advance(b, deltaSec);
            advancedThisFrame = true;
        } else {
            smoothR = driftR.get();
            smoothG = driftG.get();
            smoothB = driftB.get();
        }

        return packRGB(
                FogMath.clamp(smoothR, 0f, 1f),
                FogMath.clamp(smoothG, 0f, 1f),
                FogMath.clamp(smoothB, 0f, 1f)
        );
    }

    public void reset() {
        firstFrame        = true;
        advancedThisFrame = false;
    }

    private static int packRGB(float r, float g, float b) {
        return ((int)(r * 255f) << 16) | ((int)(g * 255f) << 8) | (int)(b * 255f);
    }
}