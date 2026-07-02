package net.atmos.atmosphere.fog;

public final class FogMath {

    private FogMath() {}

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    public static float clamp(float v, float min, float max) {
        return Math.clamp(v, min, max);
    }

    /**
     * Combined dawn/dusk horizon factor — peaks when the sun is at the
     * horizon, zero at noon and at midnight.
     *
     * This is the same dawnFactor/duskFactor/horizonMask formula already
     * used independently in DaylightFogModifier, SkyColorController, and
     * FogMixin's setupColor blend weight — collapsed here into a single
     * combined value (max of the two) since CrepuscularRayController only
     * needs "how close to horizon," not which side.
     *
     * Added for Forest Spec Task 1 (Crepuscular Rays) as an additive,
     * net-new helper. The three existing call sites each compute
     * dawnFactor/duskFactor separately (and use them independently, not
     * just their max), so they are intentionally left as-is rather than
     * refactored to call this — out of scope for this task.
     */
    public static float horizonFactor(float sunHeight, float sinAngle) {
        float horizonMask = clamp(1f - Math.abs(sunHeight) * 3f, 0f, 1f);
        float dawnFactor  = Math.max(0f,  sinAngle) * horizonMask;
        float duskFactor  = Math.max(0f, -sinAngle) * horizonMask;
        return Math.max(dawnFactor, duskFactor);
    }
}