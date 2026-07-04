package net.atmos.sunreach;

/**
 * Explainable breakdown of one Weather Attenuation evaluation (Chapter 8
 * §14, Stage Five — Weather Influence).
 *
 * Mirrors the SunReachResult / CanopyOcclusionResult pattern established by
 * earlier SunReach stages: every value that fed the final scalar is
 * retained so a future debug overlay (Chapter 8 §31/§40) can render it
 * directly without recomputation.
 *
 * rainAttenuationFactor  — illumination multiplier derived from rain alone.
 * thunderAttenuationFactor — illumination multiplier derived from thunder alone.
 * value — combined weather factor (rainAttenuationFactor * thunderAttenuationFactor).
 *
 * This is Stage Five's own internal combination only. It is NOT combined
 * with Stage One/Two/Four output here — full cross-stage combination is a
 * future task, out of scope for this evaluator.
 */
public record WeatherAttenuationResult(
        float rainAttenuationFactor,
        float thunderAttenuationFactor,
        float value
) {}