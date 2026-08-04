package net.atmos.config;

/** Seasonal Feeling System (Phase 1) configuration — backend only, no UI yet. */
public final class SeasonalConfig {

    public boolean seasonalCycleEnabled = true;
    public boolean environmentalStateInfluenceEnabled = true;
    public float   influenceStrength = 1.0f;

    public float safeInfluenceStrength() {
        return Math.clamp(influenceStrength, 0.0f, 2.0f);
    }
}