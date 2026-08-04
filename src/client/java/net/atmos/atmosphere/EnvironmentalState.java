package net.atmos.atmosphere;

import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.atmosphere.fog.biome.BiomeTraits;
import net.atmos.config.AirConfig;
import net.atmos.config.AtmosConfig;
import net.atmos.config.SeasonalConfig;
import net.atmos.seasonal.SeasonalFeelingSnapshot;
import net.atmos.seasonal.SeasonalFeelingStateManager;


public final class EnvironmentalState {

    private final AtmosphereDrifter humidityMassDrifter      = new AtmosphereDrifter(0.35f, 0.6f,  3.5f);
    private final AtmosphereDrifter stormEnergyDrifter       = new AtmosphereDrifter(0.0f,  0.55f, 2.2f);
    private final AtmosphereDrifter valleyCompressionDrifter = new AtmosphereDrifter(0.0f,  1.8f,  1.5f);
    private final AtmosphereDrifter thermalEnergyDrifter     = new AtmosphereDrifter(0.4f,  0.7f,  1.2f);
    private final AtmosphereDrifter nightDepthDrifter        = new AtmosphereDrifter(0.0f,  1.1f,  2.5f);
    private final AtmosphereDrifter skyMoistureDrifter       = new AtmosphereDrifter(0.35f, 0.35f, 2.0f);

    // --- Air Foundation (Stage 1) drifters ---
    private final AtmosphereDrifter airPressureDrifter           = new AtmosphereDrifter(0.75f, 0.12f, 1.0f);
    private final AtmosphereDrifter airDensityDrifter             = new AtmosphereDrifter(0.75f, 0.5f,  2.0f);
    private final AtmosphereDrifter atmosphericStabilityDrifter   = new AtmosphereDrifter(0.70f, 0.4f,  1.8f);
    private final AtmosphereDrifter turbulenceDrifter             = new AtmosphereDrifter(0.05f, 0.5f,  2.0f);
    private final AtmosphereDrifter aerosolDensityDrifter         = new AtmosphereDrifter(0.30f, 0.3f,  1.5f);

    // Sea-level reference and altitude range used for Air Foundation
    // altitude normalization. Mirrors HeightFogModifier's altitude bands.
    private static final float AIR_SEA_LEVEL_Y    = 64f;
    private static final float AIR_ALTITUDE_RANGE = 220f;

    // Thunder flash: not drifter-backed. Instant onset, exponential decay.
    // Half-life = ln(2) / FLASH_DECAY_RATE ≈ 0.154 seconds.
    private static final float FLASH_DECAY_RATE = 4.5f;

    // Probability of a flash per second at full thunder + full storm.
    // 0.10 = roughly one flash every 10 seconds at maximum storm intensity.
    private static final float FLASH_RATE = 0.10f;

    // Minimum thunder level to allow flashes.
    private static final float FLASH_THUNDER_MIN = 0.35f;

    private static final float SEASONAL_HUMIDITY_SHIFT_MAX = 0.12f;
    private static final float SEASONAL_THERMAL_SHIFT_MAX   = 0.12f;

    public float humidityMass      = 0.35f;
    public float stormEnergy       = 0.0f;
    public float stormApproach     = 0.0f;
    public float stormClearing     = 0.0f;
    public float thunderFlash      = 0.0f;
    public float valleyCompression = 0.0f;
    public float thermalEnergy     = 0.4f;
    public float nightDepth        = 0.0f;
    public float skyMoisture       = 0.35f;

    // --- Air Foundation (Stage 1) published state ---
    public float airPressure          = 0.75f;
    public float airDensity           = 0.75f;
    public float atmosphericStability = 0.70f;
    public float turbulence           = 0.05f;
    public float aerosolDensity       = 0.30f;

    public void advance(FogContext ctx, BiomeTraits traits, float deltaSec) {
        float rainSaturation = ctx.rain() * traits.humidity() * 0.6f;
        float humidityTarget = Math.min(1.0f, traits.humidity() + rainSaturation);
        humidityMass = FogMath.clamp(humidityMassDrifter.advance(humidityTarget, deltaSec), 0f, 1f);

        humidityTarget = applySeasonalHumidityBias(humidityTarget);


        float rainCurved  = rainIntensityCurve(ctx.rain());
        float rawStorm    = rainCurved * 0.7f + ctx.thunder() * 0.5f;
        float stormTarget = Math.min(1.0f, rawStorm * traits.weatherSensitivity());
        stormEnergy = FogMath.clamp(stormEnergyDrifter.advance(stormTarget, deltaSec), 0f, 1f);

        float rawVelocity = stormEnergyDrifter.getVelocity();
        stormApproach = FogMath.clamp( rawVelocity * 3.5f, 0f, 1f);
        stormClearing = FogMath.clamp(-rawVelocity * 3.5f, 0f, 1f);

        // --- Thunder flash ---
        // Decay existing flash first, then check for new strike.
        // Order matters: decay before trigger prevents immediate re-decay
        // on the same frame as a new strike.
        thunderFlash *= (float) Math.exp(-FLASH_DECAY_RATE * deltaSec);

        if (ctx.thunder() > FLASH_THUNDER_MIN && stormEnergy > 0.1f) {
            // Frame-rate independent probability: chance scales with deltaSec.
            // Also scales with thunder level and stormEnergy — heavy storms
            // have more frequent and brighter strikes.
            float flashChance = ctx.thunder() * stormEnergy * FLASH_RATE * deltaSec;
            if (Math.random() < flashChance) {
                thunderFlash = 1.0f;
            }
        }
        thunderFlash = FogMath.clamp(thunderFlash, 0f, 1f);

        float sunHeight     = (float) Math.cos(ctx.sunAngle());
        float rawThermal    = Math.max(0f, sunHeight);
        float thermalTarget = rawThermal * (0.5f + traits.openness() * 0.5f);
        thermalEnergy = FogMath.clamp(thermalEnergyDrifter.advance(thermalTarget, deltaSec), 0f, 1f);

        thermalTarget = applySeasonalThermalBias(thermalTarget);

        float nightTarget = Math.max(0f, -sunHeight);
        nightDepth = FogMath.clamp(nightDepthDrifter.advance(nightTarget, deltaSec), 0f, 1f);

        valleyCompression = valleyCompressionDrifter.get();

        skyMoisture = FogMath.clamp(skyMoistureDrifter.advance(humidityMass * 0.85f, deltaSec), 0f, 1f);

        advanceAir(ctx, deltaSec);
    }

    /**
     * Air Foundation (Stage 1). Publishes pressure, density, atmospheric
     * stability, and two forward-looking foundation signals (turbulence,
     * aerosol density) that future Wind and Haze systems will consume.
     *
     * Never renders. Never modifies fog, clouds, lighting, visibility, or
     * weather directly — only publishes atmospheric state, per Air Density
     * & Pressure Architecture.md's design rules.
     *
     * Reads stormEnergy/stormApproach/stormClearing/thermalEnergy/humidityMass
     * computed earlier in this same advance() call — no duplicate humidity
     * or thermal calculation is introduced here.
     */
    private void advanceAir(FogContext ctx, float deltaSec) {
        AirConfig airCfg = AtmosConfig.get().air;
        if (!airCfg.airSimulationEnabled) return;

        float airDeltaSec = deltaSec * airCfg.safeSimulationSpeed();

        float altitudeNorm = FogMath.clamp(
                (ctx.cameraY() - AIR_SEA_LEVEL_Y) / AIR_ALTITUDE_RANGE, 0f, 1f);

        float densityTarget = FogMath.clamp(
                1f - altitudeNorm * 0.55f - (thermalEnergy - 0.5f) * 0.15f - humidityMass * 0.05f,
                0.05f, 1f);
        airDensity = FogMath.clamp(airDensityDrifter.advance(densityTarget, airDeltaSec), 0f, 1f);

        float pressureTarget = FogMath.clamp(
                1f - altitudeNorm * 0.65f - stormEnergy * 0.35f - stormApproach * 0.10f,
                0.05f, 1f);
        airPressure = FogMath.clamp(airPressureDrifter.advance(pressureTarget, airDeltaSec), 0f, 1f);

        float stabilityTarget = FogMath.clamp(
                1f - stormEnergy * 0.60f - stormApproach * 0.25f - stormClearing * 0.15f,
                0f, 1f);
        atmosphericStability = FogMath.clamp(
                atmosphericStabilityDrifter.advance(stabilityTarget, airDeltaSec), 0f, 1f);

        float turbulenceTarget = FogMath.clamp(
                stormEnergy * 0.70f + stormApproach * 0.20f + stormClearing * 0.15f,
                0f, 1f);
        turbulence = FogMath.clamp(turbulenceDrifter.advance(turbulenceTarget, airDeltaSec), 0f, 1f);

        float aerosolTarget = FogMath.clamp(
                (1f - humidityMass) * 0.55f + thermalEnergy * 0.25f
                        - ctx.rain() * 0.50f - stormEnergy * 0.20f,
                0f, 1f);
        aerosolDensity = FogMath.clamp(
                aerosolDensityDrifter.advance(aerosolTarget, airDeltaSec), 0f, 1f);
    }

    public void snapToTargets(FogContext ctx, BiomeTraits traits) {
        float rainSaturation = ctx.rain() * traits.humidity() * 0.6f;
        float humidityTarget = FogMath.clamp(Math.min(1.0f, traits.humidity() + rainSaturation), 0f, 1f);

        float rainCurved  = rainIntensityCurve(ctx.rain());
        float rawStorm    = rainCurved * 0.7f + ctx.thunder() * 0.5f;
        float stormTarget = FogMath.clamp(Math.min(1.0f, rawStorm * traits.weatherSensitivity()), 0f, 1f);

        float sunHeight      = (float) Math.cos(ctx.sunAngle());
        float rawThermal     = Math.max(0f, sunHeight);
        float thermalTarget  = FogMath.clamp(rawThermal * (0.5f + traits.openness() * 0.5f), 0f, 1f);
        float nightTarget    = FogMath.clamp(Math.max(0f, -sunHeight), 0f, 1f);
        float skyMoistTarget = FogMath.clamp(humidityTarget * 0.85f, 0f, 1f);

        humidityMassDrifter .snap(humidityTarget);
        stormEnergyDrifter  .snap(stormTarget);
        thermalEnergyDrifter.snap(thermalTarget);
        nightDepthDrifter   .snap(nightTarget);
        skyMoistureDrifter  .snap(skyMoistTarget);

        humidityMass  = humidityTarget;
        stormEnergy   = stormTarget;
        stormApproach = 0f;
        stormClearing = 0f;
        thunderFlash  = 0f;
        thermalEnergy = thermalTarget;
        nightDepth    = nightTarget;
        skyMoisture   = skyMoistTarget;

        if (AtmosConfig.get().air.airSimulationEnabled) {
            float altitudeNorm = FogMath.clamp(
                    (ctx.cameraY() - AIR_SEA_LEVEL_Y) / AIR_ALTITUDE_RANGE, 0f, 1f);

            float densityTarget = FogMath.clamp(
                    1f - altitudeNorm * 0.55f - (thermalTarget - 0.5f) * 0.15f - humidityTarget * 0.05f,
                    0.05f, 1f);
            float pressureTarget = FogMath.clamp(
                    1f - altitudeNorm * 0.65f - stormTarget * 0.35f, 0.05f, 1f);
            float stabilityTarget = FogMath.clamp(1f - stormTarget * 0.60f, 0f, 1f);
            float turbulenceTarget = FogMath.clamp(stormTarget * 0.70f, 0f, 1f);
            float aerosolTarget = FogMath.clamp(
                    (1f - humidityTarget) * 0.55f + thermalTarget * 0.25f
                            - ctx.rain() * 0.50f - stormTarget * 0.20f,
                    0f, 1f);

            airDensityDrifter          .snap(densityTarget);
            airPressureDrifter         .snap(pressureTarget);
            atmosphericStabilityDrifter.snap(stabilityTarget);
            turbulenceDrifter          .snap(turbulenceTarget);
            aerosolDensityDrifter      .snap(aerosolTarget);

            airDensity            = densityTarget;
            airPressure           = pressureTarget;
            atmosphericStability  = stabilityTarget;
            turbulence            = turbulenceTarget;
            aerosolDensity        = aerosolTarget;
        }
    }

    public void pushValleyCompression(float rawValleyFactor, float deltaSec) {
        valleyCompression = valleyCompressionDrifter.advance(rawValleyFactor, deltaSec);
    }

    public void reset() {
        humidityMassDrifter     .snap(0.35f);
        stormEnergyDrifter      .snap(0.0f);
        valleyCompressionDrifter.snap(0.0f);
        thermalEnergyDrifter    .snap(0.4f);
        nightDepthDrifter       .snap(0.0f);
        skyMoistureDrifter      .snap(0.35f);

        airPressureDrifter          .snap(0.75f);
        airDensityDrifter           .snap(0.75f);
        atmosphericStabilityDrifter .snap(0.70f);
        turbulenceDrifter           .snap(0.05f);
        aerosolDensityDrifter       .snap(0.30f);

        humidityMass      = 0.35f;
        stormEnergy       = 0.0f;
        stormApproach     = 0.0f;
        stormClearing     = 0.0f;
        thunderFlash      = 0.0f;
        valleyCompression = 0.0f;
        thermalEnergy     = 0.4f;
        nightDepth        = 0.0f;
        skyMoisture       = 0.35f;

        airPressure           = 0.75f;
        airDensity            = 0.75f;
        atmosphericStability  = 0.70f;
        turbulence            = 0.05f;
        aerosolDensity        = 0.30f;
    }

    private static float rainIntensityCurve(float rain) {
        return (float) Math.pow(FogMath.clamp(rain, 0f, 1f), 1.8f);
    }
    private float applySeasonalHumidityBias(float target) {
        SeasonalConfig cfg = AtmosConfig.get().seasonal;
        if (!cfg.seasonalCycleEnabled || !cfg.environmentalStateInfluenceEnabled) return target;

        SeasonalFeelingSnapshot season = SeasonalFeelingStateManager.get();
        float scale = season.calendar().seasonStrength() * cfg.safeInfluenceStrength();
        float shift = season.influence().humidityInfluence() * SEASONAL_HUMIDITY_SHIFT_MAX * scale;
        return FogMath.clamp(target + shift, 0f, 1f);
    }

    private float applySeasonalThermalBias(float target) {
        SeasonalConfig cfg = AtmosConfig.get().seasonal;
        if (!cfg.seasonalCycleEnabled || !cfg.environmentalStateInfluenceEnabled) return target;

        SeasonalFeelingSnapshot season = SeasonalFeelingStateManager.get();
        float scale = season.calendar().seasonStrength() * cfg.safeInfluenceStrength();
        float shift = season.influence().temperatureInfluence() * SEASONAL_THERMAL_SHIFT_MAX * scale;
        return FogMath.clamp(target + shift, 0f, 1f);
    }

    public float getHumidityMass()  { return humidityMass;  }
    public float getSkyMoisture()   { return skyMoisture;   }
    public float getStormEnergy()   { return stormEnergy;   }
    public float getNightDepth()    { return nightDepth;    }
    public float getThermalEnergy() { return thermalEnergy; }
    public float getStormApproach() { return stormApproach; }
    public float getStormClearing() { return stormClearing; }
    public float getThunderFlash()  { return thunderFlash;  }

    public float getAirPressure()          { return airPressure;          }
    public float getAirDensity()           { return airDensity;           }
    public float getAtmosphericStability() { return atmosphericStability; }
    public float getTurbulence()           { return turbulence;           }
    public float getAerosolDensity()       { return aerosolDensity;       }
}