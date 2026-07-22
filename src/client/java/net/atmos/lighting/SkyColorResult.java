package net.atmos.lighting;

import net.atmos.render.RenderColor;

/**
 * Explainable result of one SkyColorProvider evaluation — Appendix ZC §3.
 * Owns exactly the two values SkyColorProvider is the sole producer of.
 */
public record SkyColorResult(
        RenderColor zenith,
        RenderColor horizon
) {
    public SkyColorResult {
        if (zenith == null)  throw new IllegalArgumentException("zenith must not be null");
        if (horizon == null) throw new IllegalArgumentException("horizon must not be null");
    }
}