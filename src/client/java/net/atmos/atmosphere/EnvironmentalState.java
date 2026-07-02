package net.atmos.atmosphere;

import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.atmosphere.fog.biome.BiomeTraits;

/**
 * Persistent simulation of the world's atmospheric condition.
 *
 * NOT a fog state. The environmental pressure that fog, sky, and color
 * systems all read from. Because every system reads the same drifting values,
 * storms, nights, and biome crossings feel like unified world events.
 *
 * humidityMass      — accumulated air moisture. Builds slowly in humid biomes.
 * stormEnergy       — emotional storm intensity. Rises slower than rain level.
 * stormApproach     — positive drifter velocity: storm is building.
 * stormClearing     — negative drifter velocity: storm is actively dissipating.
 * thunderFlash      — per-strike flash signal. Instant onset, fast decay (~0.3s).
 * thermalEnergy     — daytime heat. Counteracts humidity.
 * nightDepth        — smooth 0→1 as night deepens.
 * valleyCompression — terrain enclosure. Written by ValleyFogModifier.
 * skyMoisture       — upper sky haze. Lags behind humidityMass.
 *
 * All drifter-backed fields clamped to [0,1] after advance.
 *
 * thunderFlash is not drifter-backed — lightning requires instant onset.
 * A drifter would introduce a build delay before the flash peaks.
 * Instead: snap to 1.0 on trigger, decay via exponential with FLASH_DECAY_RATE.
 * At FLASH_DECAY_RATE=4.5, half-life ≈ 0.15s, fully decayed in ~0.5s.
 *
 * Flash probability is frame-rate independent: chance = thunder * stormEnergy
 * * FLASH_RATE * deltaSec. At full storm ~0.10 flashes/second on average.
 */
public final class EnvironmentalState {

    private final AtmosphereDrifter humidityMassDrifter      = new AtmosphereDrifter(0.35f, 0.6f,  3.5f);
    private final AtmosphereDrifter stormEnergyDrifter       = new AtmosphereDrifter(0.0f,  0.55f, 2.2f);
    private final AtmosphereDrifter valleyCompressionDrifter = new AtmosphereDrifter(0.0f,  1.8f,  1.5f);
    private final AtmosphereDrifter thermalEnergyDrifter     = new AtmosphereDrifter(0.4f,  0.7f,  1.2f);
    private final AtmosphereDrifter nightDepthDrifter        = new AtmosphereDrifter(0.0f,  1.1f,  2.5f);
    private final AtmosphereDrifter skyMoistureDrifter       = new AtmosphereDrifter(0.35f, 0.35f, 2.0f);

    // Thunder flash: not drifter-backed. Instant onset, exponential decay.
    // Half-life = ln(2) / FLASH_DECAY_RATE ≈ 0.154 seconds.
    private static final float FLASH_DECAY_RATE = 4.5f;

    // Probability of a flash per second at full thunder + full storm.
    // 0.10 = roughly one flash every 10 seconds at maximum storm intensity.
    private static final float FLASH_RATE = 0.10f;

    // Minimum thunder level to allow flashes.
    private static final float FLASH_THUNDER_MIN = 0.35f;

    public float humidityMass      = 0.35f;
    public float stormEnergy       = 0.0f;
    public float stormApproach     = 0.0f;
    public float stormClearing     = 0.0f;
    public float thunderFlash      = 0.0f;
    public float valleyCompression = 0.0f;
    public float thermalEnergy     = 0.4f;
    public float nightDepth        = 0.0f;
    public float skyMoisture       = 0.35f;

    public void advance(FogContext ctx, BiomeTraits traits, float deltaSec) {
        float rainSaturation = ctx.rain() * traits.humidity() * 0.6f;
        float humidityTarget = Math.min(1.0f, traits.humidity() + rainSaturation);
        humidityMass = FogMath.clamp(humidityMassDrifter.advance(humidityTarget, deltaSec), 0f, 1f);

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

        float nightTarget = Math.max(0f, -sunHeight);
        nightDepth = FogMath.clamp(nightDepthDrifter.advance(nightTarget, deltaSec), 0f, 1f);

        valleyCompression = valleyCompressionDrifter.get();

        skyMoisture = FogMath.clamp(skyMoistureDrifter.advance(humidityMass * 0.85f, deltaSec), 0f, 1f);
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

        humidityMass      = 0.35f;
        stormEnergy       = 0.0f;
        stormApproach     = 0.0f;
        stormClearing     = 0.0f;
        thunderFlash      = 0.0f;
        valleyCompression = 0.0f;
        thermalEnergy     = 0.4f;
        nightDepth        = 0.0f;
        skyMoisture       = 0.35f;
    }

    private static float rainIntensityCurve(float rain) {
        return (float) Math.pow(FogMath.clamp(rain, 0f, 1f), 1.8f);
    }

    public float getSkyMoisture()   { return skyMoisture;   }
    public float getStormEnergy()   { return stormEnergy;   }
    public float getNightDepth()    { return nightDepth;    }
    public float getThermalEnergy() { return thermalEnergy; }
    public float getStormApproach() { return stormApproach; }
    public float getStormClearing() { return stormClearing; }
    public float getThunderFlash()  { return thunderFlash;  }
}