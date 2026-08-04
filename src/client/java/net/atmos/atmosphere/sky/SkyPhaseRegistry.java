package net.atmos.atmosphere.sky;

import net.atmos.render.RenderColor;

/**
 * Elevation-ordered color anchors spanning midnight to solar noon.
 * SkyPhaseModel interpolates continuously between neighboring anchors.
 */
public final class SkyPhaseRegistry {

    private SkyPhaseRegistry() {}

    public static final SkyPhaseAnchor[] ANCHORS = {
            anchor(-90f, 0.010f, 0.010f, 0.028f, 0.014f, 0.014f, 0.032f),
            anchor(-18f, 0.015f, 0.015f, 0.045f, 0.020f, 0.020f, 0.050f),
            anchor(-12f, 0.035f, 0.032f, 0.085f, 0.055f, 0.045f, 0.100f),
            anchor(-6f,  0.070f, 0.065f, 0.170f, 0.140f, 0.110f, 0.230f),
            anchor(-2f,  0.150f, 0.130f, 0.300f, 0.520f, 0.320f, 0.420f),
            anchor(0f,   0.220f, 0.190f, 0.380f, 0.780f, 0.380f, 0.280f),
            anchor(3f,   0.280f, 0.320f, 0.560f, 0.920f, 0.560f, 0.300f),
            anchor(10f,  0.320f, 0.520f, 0.860f, 0.780f, 0.740f, 0.780f),
            anchor(30f,  0.350f, 0.560f, 0.900f, 0.700f, 0.800f, 0.950f),
            anchor(90f,  0.330f, 0.560f, 0.920f, 0.680f, 0.790f, 0.960f)
    };

    private static SkyPhaseAnchor anchor(float elevation,
                                         float zr, float zg, float zb,
                                         float hr, float hg, float hb) {
        return new SkyPhaseAnchor(elevation, new RenderColor(zr, zg, zb), new RenderColor(hr, hg, hb));
    }
}