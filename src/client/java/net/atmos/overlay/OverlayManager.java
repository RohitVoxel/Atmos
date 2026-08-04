package net.atmos.overlay;

import net.atmos.atmosphere.fog.FogDrifter;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.config.AtmosConfig;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns every overlay value. Individual systems (Season, Rain, Air, future
 * Wind/Weather) publish only their own named contribution per overlay type.
 * This manager sums contributions, clamps to [0,1], and smooths the result
 * toward that target using the existing FogDrifter primitive.
 *
 * Never renders. Never queries the world. The renderer only consumes
 * getValue() and never recomputes overlay state.
 */
public final class OverlayManager {

    private static final float BASE_BUILD_SPEED = 0.10f;
    private static final float BASE_CLEAR_SPEED = 0.16f;

    private final Map<OverlayType, Map<OverlaySource, Float>> contributions = new EnumMap<>(OverlayType.class);
    private final Map<OverlayType, FogDrifter> drifters = new EnumMap<>(OverlayType.class);

    public OverlayManager() {
        for (OverlayType type : OverlayType.values()) {
            contributions.put(type, new EnumMap<>(OverlaySource.class));
            drifters.put(type, new FogDrifter(0f, BASE_BUILD_SPEED, BASE_CLEAR_SPEED));
        }
    }

    public void setContribution(OverlayType type, OverlaySource source, float value) {
        contributions.get(type).put(source, FogMath.clamp(value, 0f, 1f));
    }

    public void clearContribution(OverlayType type, OverlaySource source) {
        contributions.get(type).remove(source);
    }

    public float targetFor(OverlayType type) {
        float sum = 0f;
        for (float v : contributions.get(type).values()) {
            sum += v;
        }
        return FogMath.clamp(sum, 0f, 1f);
    }

    public float contributionFrom(OverlaySource source) {
        float sum = 0f;
        for (OverlayType type : OverlayType.values()) {
            Float v = contributions.get(type).get(source);
            if (v != null) sum += v;
        }
        return sum;
    }

    public void update(float deltaSec) {
        if (!AtmosConfig.get().overlay.overlaysEnabled) return;

        float speedMult = AtmosConfig.get().overlay.safeUpdateSpeed();
        for (OverlayType type : OverlayType.values()) {
            drifters.get(type).advance(targetFor(type), deltaSec * speedMult);
        }
    }

    public float getValue(OverlayType type) {
        return drifters.get(type).get();
    }

    public void reset() {
        for (OverlayType type : OverlayType.values()) {
            contributions.get(type).clear();
            drifters.get(type).snap(0f);
        }
    }
}
