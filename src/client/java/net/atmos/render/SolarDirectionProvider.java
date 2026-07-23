package net.atmos.render;

import net.atmos.atmosphere.fog.FogMath;
import net.minecraft.world.phys.Vec3;

/**
 * Global Solar Direction Provider — Appendix ZB Blocker 7, Appendix ZD.
 *
 * Reuses Appendix F §3.1's approved convention (sunAngleRadians as azimuth,
 * asin(cos(sunAngleRadians)) as elevation) rather than inventing a second
 * azimuth mapping — same reasoning already applied by SunReachEvaluator and
 * AtmosphericLightingPipeline.
 *
 * V_sun = <cosE*sinA, sinE, cosE*cosA>, normalized per Blocker 7 rule 5.
 * cosElevation >= 0 always (elevation in [-pi/2,pi/2]), so raw is never the
 * zero vector — normalize() cannot produce NaN here.
 *
 * Stateless, deterministic, O(1).
 */
public final class SolarDirectionProvider {

    private SolarDirectionProvider() {}

    public static SolarDirectionResult evaluate(float sunAngleRadians) {
        float sunHeight = (float) Math.cos(sunAngleRadians);
        float elevationRadians = (float) Math.asin(FogMath.clamp(sunHeight, -1f, 1f));
        float azimuthRadians = sunAngleRadians;

        double cosElevation = Math.cos(elevationRadians);
        double sinElevation = Math.sin(elevationRadians);

        Vec3 raw = new Vec3(
                cosElevation * Math.sin(azimuthRadians),
                sinElevation,
                cosElevation * Math.cos(azimuthRadians)
        );

        return new SolarDirectionResult(elevationRadians, azimuthRadians, raw.normalize());
    }
}