package net.atmos.atmosphere.fog.modifiers;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.*;

public final class RenderDistanceModifier implements FogModifier {

    private static final float RD_REFERENCE = 12.0f;

    @Override
    public FogState apply(FogState fog, FogContext ctx, EnvironmentalState env) {
        int   rd    = ctx.renderDistance();
        float scale = FogMath.clamp(rd / RD_REFERENCE, 0.6f, 2.0f);
        float rdMax = rd * 16.0f * 0.85f;
        float end   = Math.min(fog.end() * scale, rdMax);

        float naturalRatio = (fog.end() > 0f)
                ? FogMath.clamp(fog.start() / fog.end(), 0.1f, 0.9f) : 0.3f;
        float rdNorm      = FogMath.clamp((rd - RD_REFERENCE) / RD_REFERENCE, -1f, 1f);
        float targetRatio = FogMath.clamp(naturalRatio + rdNorm * -0.08f, 0.15f, 0.82f);
        float start       = Math.min(end * targetRatio, rdMax * 0.7f);

        return fog.withDistances(start, end);
    }
}