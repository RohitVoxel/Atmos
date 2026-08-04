package net.atmos.atmosphere.sky;

import net.atmos.render.RenderColor;

/** One reference point in the sky's elevation-to-color curve. */
public record SkyPhaseAnchor(
        float elevationDegrees,
        RenderColor zenith,
        RenderColor horizon
) {
    public SkyPhaseAnchor {
        if (zenith == null) throw new IllegalArgumentException("zenith must not be null");
        if (horizon == null) throw new IllegalArgumentException("horizon must not be null");
    }
}