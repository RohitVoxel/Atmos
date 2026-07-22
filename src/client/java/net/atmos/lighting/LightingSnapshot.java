package net.atmos.lighting;

import net.atmos.render.RenderColor;

/**
 * Immutable lighting payload — Appendix ZB §II ("LightingSnapshot"),
 * Appendix ZC §2 (sole production chain: SkyColorProvider ->
 * AtmosphericLightingPipeline -> LightingSnapshot).
 *
 * Published once per evaluation by AtmosphericLightingPipeline. Every
 * field is immutable; instances are never mutated after construction. No
 * manager/publisher exists yet — lock-free publication and consumption
 * are runtime-wiring concerns explicitly out of scope for this phase.
 *
 * lightIntensity / shadowStrength are documented in Appendix ZB §II as
 * Range: [0.0, 1.0] each — both bounds are enforced below.
 */
public record LightingSnapshot(
        float lightIntensity,
        float shadowStrength,
        RenderColor skyTint,
        RenderColor weatherTint
) {
    public LightingSnapshot {
        if (!Float.isFinite(lightIntensity) || lightIntensity < 0f || lightIntensity > 1f) {
            throw new IllegalArgumentException(
                    "lightIntensity must be within [0,1], got " + lightIntensity);
        }
        if (!Float.isFinite(shadowStrength) || shadowStrength < 0f || shadowStrength > 1f) {
            throw new IllegalArgumentException(
                    "shadowStrength must be within [0,1], got " + shadowStrength);
        }
        if (skyTint == null) {
            throw new IllegalArgumentException("skyTint must not be null");
        }
        if (weatherTint == null) {
            throw new IllegalArgumentException("weatherTint must not be null");
        }
    }

    /** Neutral baseline reserved for a future LightingSnapshot state manager's initial/reset value. */
    public static LightingSnapshot neutral() {
        return new LightingSnapshot(1.0f, 0f, new RenderColor(1f, 1f, 1f), new RenderColor(1f, 1f, 1f));
    }
}