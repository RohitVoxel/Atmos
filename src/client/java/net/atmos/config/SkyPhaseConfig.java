package net.atmos.config;

public final class SkyPhaseConfig {

    public boolean enhancedSkyEnabled     = true;
    public float   transitionSpeedMultiplier = 1.0f;
    public float   skyColorIntensity      = 1.0f;
    public float   twilightIntensity      = 1.0f;
    public float   nightBrightness        = 0.3f;
    public boolean blueHourEnabled = true;


    
    public float safeTransitionSpeedMultiplier() {
        return Math.clamp(transitionSpeedMultiplier, 0.25f, 4.0f);
    }

    public float safeSkyColorIntensity() {
        return Math.clamp(skyColorIntensity, 0.0f, 1.5f);
    }

    public float safeTwilightIntensity() {
        return Math.clamp(twilightIntensity, 0.0f, 2.0f);
    }

    public float safeNightBrightness() {
        return Math.clamp(nightBrightness, 0.0f, 1.0f);
    }
}