package net.atmos.atmosphere.sky;

import net.atmos.render.RenderColor;

/** Continuous zenith/horizon colors for a given sun elevation, plus the named phase. */
public record SkyPhaseResult(
        SkyPhase phase,
        float elevationDegrees,
        RenderColor zenith,
        RenderColor horizon
) {}