package net.atmos.config;

public final class FogConfig {

    public float   fogIntensity          = 1.0f;
    public float   weatherIntensity      = 1.0f;
    public float   transitionSpeed       = 1.0f;
    public float   nightFogStrength      = 1.0f;
    public boolean biomeFogBlending      = true;

    // -------------------------------------------------------------------------
    // Visibility Safety Floor
    // -------------------------------------------------------------------------
    // Prevents modifier stacking (night + storm + valley + humidity) from
    // compressing fog end below a gameplay-safe minimum.
    //
    // visibilityFloorEnabled  — master toggle. Disable to remove all clamping.
    // visibilityFloorFraction — floor = biome base end * this fraction.
    //                           0.45 means fog end can't drop below 45% of the
    //                           biome's natural clear-weather end distance.
    //                           Lower = more atmosphere, less safety.
    //                           Higher = safer, but storms feel weaker.
    // visibilityFloorAbsolute — hard minimum in blocks, regardless of biome.
    //                           Prevents tiny biomes (swamp end=52) from having
    //                           an absurdly close floor.
    //
    // Effective floor = max(base_end * fraction, absolute_minimum)
    // Then: fog end = max(pipeline_result, effective_floor)
    //
    // Default values keep survival gameplay safe while preserving dramatic
    // atmosphere in most conditions. Players who want pure cinema can lower
    // visibilityFloorFraction toward 0.30 or disable entirely.
    // -------------------------------------------------------------------------
    public boolean visibilityFloorEnabled   = true;
    public float   visibilityFloorFraction  = 0.45f;
    public float   visibilityFloorAbsolute  = 24.0f;

    // -------------------------------------------------------------------------
    // Feature toggles — each major system can be disabled independently.
    // Useful for debugging, performance testing, and shader compatibility.
    // -------------------------------------------------------------------------
    public boolean fogEnabled          = true;
    public boolean skyEnabled          = true;
    public boolean weatherEffects      = true;
    public boolean valleyFog           = true;
    public boolean dryAtmosphere       = true;
    public boolean nightCompression    = true;

    // Added: Forest Spec Task 1 — Crepuscular Rays.
    // Independent of fogEnabled in principle (it's a sky-layer render effect,
    // not a fog modifier) but CrepuscularRayRenderer also checks fogEnabled
    // as a master "is Atmos visually active at all" gate, consistent with
    // how every other optional visual system in this config behaves.
    public boolean crepuscularRays     = true;

    public float safeFogIntensity() {
        return Math.clamp(fogIntensity, 0.1f, 3.0f);
    }

    public float safeWeatherIntensity() {
        return Math.clamp(weatherIntensity, 0.0f, 2.0f);
    }

    /** Base duration 6.25s divided by transitionSpeed. */
    public float blendDuration() {
        return 6.25f / Math.clamp(transitionSpeed, 0.1f, 5.0f);
    }

    public float safeNightFogStrength() {
        return Math.clamp(nightFogStrength, 0.0f, 2.0f);
    }

    public float safeVisibilityFloorFraction() {
        return Math.clamp(visibilityFloorFraction, 0.10f, 0.80f);
    }

    public float safeVisibilityFloorAbsolute() {
        return Math.clamp(visibilityFloorAbsolute, 8.0f, 64.0f);
    }
}