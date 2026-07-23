package net.atmos.render;

import net.atmos.atmosphere.fog.FogMath;

/**
 * Distance Evaluation — Appendix ZB Blocker 4, Appendix ZC/ZD §3.
 *
 * D_render is computed internally as renderDistanceChunks * 16 (blocks),
 * per Appendix ZD's ownership resolution (FogContext.renderDistance() * 16).
 * The caller supplies the already-captured chunk count rather than this
 * evaluator depending on FogContext directly, matching the primitive
 * -extraction idiom used throughout this codebase (e.g. DirectorInputs).
 *
 * environmentalVisibility is AtmosphereDirector.FogDensity
 * (DirectorState.fogDensity()), already bounded to [0,1] by its owner —
 * not re-clamped here.
 *
 * D_max is floored to 0.001 per Blocker 4 §5, guaranteeing the smoothstep
 * edges never coincide even at maximum visibility. GLSL-style 3-argument
 * smoothstep is expanded manually since FogMath.smoothstep() is the
 * single-argument form. FADE_MARGIN_START is read from RenderingMathConstants.
 *
 * Stateless, deterministic, O(1).
 */
public final class DistanceEvaluationEvaluator {

    private DistanceEvaluationEvaluator() {}

    private static final float MIN_MAX_RENDER_DISTANCE = 0.001f;
    private static final float BLOCKS_PER_CHUNK = 16f;

    public static DistanceEvaluationResult evaluate(
            float cameraDistance, float environmentalVisibility, int renderDistanceChunks) {

        float renderDistanceBlocks = renderDistanceChunks * BLOCKS_PER_CHUNK;

        float maxRenderDistance = Math.max(
                renderDistanceBlocks * (1f - environmentalVisibility), MIN_MAX_RENDER_DISTANCE);

        float edge0 = maxRenderDistance * RenderingMathConstants.FADE_MARGIN_START;
        float edge1 = maxRenderDistance;

        float t = FogMath.clamp((cameraDistance - edge0) / (edge1 - edge0), 0f, 1f);
        float fadeWeight = 1f - FogMath.smoothstep(t);

        return new DistanceEvaluationResult(maxRenderDistance, fadeWeight);
    }
}