package net.atmos.atmosphere.sky;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.render.RenderColor;

/**
 * Maps sun elevation to smoothly interpolated zenith/horizon colors.
 * Stateless and deterministic — identical elevation always yields an identical result.
 */
public final class SkyPhaseModel {

    private SkyPhaseModel() {}

    public static SkyPhaseResult evaluate(float elevationDegrees) {
        SkyPhaseAnchor[] anchors = SkyPhaseRegistry.ANCHORS;

        if (elevationDegrees <= anchors[0].elevationDegrees()) {
            SkyPhaseAnchor a = anchors[0];
            return new SkyPhaseResult(SkyPhase.NIGHT, elevationDegrees, a.zenith(), a.horizon());
        }
        int last = anchors.length - 1;
        if (elevationDegrees >= anchors[last].elevationDegrees()) {
            SkyPhaseAnchor a = anchors[last];
            return new SkyPhaseResult(SkyPhase.DAY, elevationDegrees, a.zenith(), a.horizon());
        }

        for (int i = 0; i < last; i++) {
            SkyPhaseAnchor lower = anchors[i];
            SkyPhaseAnchor upper = anchors[i + 1];
            if (elevationDegrees < upper.elevationDegrees()) {
                float span = upper.elevationDegrees() - lower.elevationDegrees();
                float t = FogMath.smoothstep(FogMath.clamp((elevationDegrees - lower.elevationDegrees()) / span, 0f, 1f));

                RenderColor zenith = lerpColor(lower.zenith(), upper.zenith(), t);
                RenderColor horizon = lerpColor(lower.horizon(), upper.horizon(), t);
                SkyPhase phase = SkyPhase.fromElevationDegrees(elevationDegrees);

                return new SkyPhaseResult(phase, elevationDegrees, zenith, horizon);
            }
        }

        SkyPhaseAnchor fallback = anchors[last];
        return new SkyPhaseResult(SkyPhase.DAY, elevationDegrees, fallback.zenith(), fallback.horizon());
    }

    private static RenderColor lerpColor(RenderColor a, RenderColor b, float t) {
        return new RenderColor(
                FogMath.lerp(a.red(), b.red(), t),
                FogMath.lerp(a.green(), b.green(), t),
                FogMath.lerp(a.blue(), b.blue(), t)
        );
    }
}