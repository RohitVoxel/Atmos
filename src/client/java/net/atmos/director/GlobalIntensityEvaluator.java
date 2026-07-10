package net.atmos.director;

/**
 * Global Intensity Controller evaluator — Chapter 11 §11.26, Stage 5.
 *
 * Implements exactly the formula specified by Appendix Q, the authoritative
 * clarification of §11.26's previously unspecified mathematics. No
 * coefficient, curve, smoothing term, or additional input is introduced
 * beyond what Appendix Q defines (§Q.9 — "No Hidden Logic").
 *
 * --- Formula (Appendix Q §Q.5) ---
 *
 *     GlobalIntensity
 *         = Baseline
 *         + (HeroMomentScore × PeakBoostMax × (1 − VisualFatigue))
 *
 * --- Domain-object input contract ---
 *
 * This evaluator consumes {@link HeroMomentResult} rather than a bare
 * {@code float}, matching the established Atmos convention of passing
 * domain objects between evaluators rather than pre-extracted scalars
 * (e.g. {@code HeroMomentEvaluator.evaluate(TierAResult, ...)},
 * {@code ClusterConfidenceEvaluator.evaluate(Cluster, CameraSnapshot)}).
 * Extraction of {@code heroMoment.value()} is therefore this evaluator's
 * own responsibility, not the caller's — preserving encapsulation and
 * leaving room for a future revision to consult additional
 * {@code HeroMomentResult} fields without changing the call site.
 *
 * visualFatigue remains a primitive {@code float}: it is not itself a
 * Director-produced explainable result record (it has no sub-factor
 * breakdown to preserve) — it is a single accumulated scalar owned
 * directly by {@link AtmosphereDirector}, so no wrapping object exists
 * for it to consume instead.
 *
 * --- Input range guarantees (no re-clamping) ---
 *
 * {@code heroMoment.value()} is a product of four factors
 * (tierAFactor, stormClearingFactor, goldenHourFactor, humidityFactor)
 * each already guaranteed within [0,1] by their respective owners
 * (TierAEvaluator's weighted geometric product, EnvironmentalState's
 * clamped drifters, FogMath.horizonFactor's clamped composition) — the
 * product is therefore already within [0,1] by construction.
 *
 * visualFatigue is sourced from {@link AtmosphereDirector}'s own Stage 4
 * output, already clamped to [0,1] via {@code FogMath.clamp} before this
 * evaluator is ever called, and independently re-validated by
 * {@link DirectorState}'s compact constructor.
 *
 * Per the identical precedent already established by TierAEvaluator and
 * WeatherAttenuationEvaluator ("input signals already guaranteed in range
 * by their upstream owner"), this evaluator does not re-clamp either
 * input — doing so would duplicate a guarantee this evaluator does not
 * own and would silently mask an upstream defect rather than surface it.
 *
 * --- Output range ---
 *
 * Given both inputs are within [0,1], the result is mathematically
 * bounded to [Baseline, Baseline + PeakBoostMax] = [1.0, 1.12] by
 * construction — no explicit clamp is required or applied.
 *
 * --- Determinism, threading, performance ---
 *
 * Stateless, side-effect-free, O(1): one field read, two multiplications,
 * one subtraction, one addition, one record allocation. No caching, no
 * mutable static state, no world access. Deterministic — identical
 * inputs always produce an identical result. Safe for Simulation Thread
 * use, matching every other Director/Confidence/SunReach evaluator in
 * the codebase.
 *
 * --- Task boundary ---
 *
 * Stage 5 only. This evaluator does not integrate with fog, mist,
 * crepuscular rays, exposure, ambient density, APS, or ALSS — per
 * Appendix Q §Q.8, GlobalIntensity is owned exclusively by the
 * Atmosphere Director and published read-only for future consumers. No
 * such consumer is wired in this task.
 */
public final class GlobalIntensityEvaluator {

    private GlobalIntensityEvaluator() {}

    public static GlobalIntensityResult evaluate(HeroMomentResult heroMoment, float visualFatigue) {
        float heroMomentScore = heroMoment.value();

        float peakBoost          = heroMomentScore * DirectorWeights.GLOBAL_INTENSITY_PEAK_BOOST_MAX;
        float effectivePeakBoost = peakBoost * (1f - visualFatigue);
        float value              = DirectorWeights.GLOBAL_INTENSITY_BASELINE + effectivePeakBoost;

        return new GlobalIntensityResult(heroMomentScore, visualFatigue, peakBoost, effectivePeakBoost, value);
    }
}