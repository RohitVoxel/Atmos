package net.atmos.director;

/**
 * Explainable breakdown of one Global Intensity evaluation, per Chapter 11
 * §11.26 ("Global Intensity Controller") as mathematically specified by
 * Appendix Q.
 *
 * Per Appendix Q §Q.5, the canonical formula is:
 *
 *     GlobalIntensity = Baseline + (HeroMomentScore × PeakBoostMax × (1 − VisualFatigue))
 *
 * Every intermediate term is preserved here rather than only the final
 * scalar, matching the explainability pattern already established by
 * {@code TierAResult}, {@code HeroMomentResult}, and every other Atmos
 * evaluator result record.
 *
 * heroMomentScore    — the Hero Moment Score consumed for this evaluation
 *                       (Appendix Q §Q.3), extracted internally by
 *                       {@link GlobalIntensityEvaluator} from the
 *                       {@code HeroMomentResult} domain object it
 *                       received. Already guaranteed within [0,1] by its
 *                       own producer (see GlobalIntensityEvaluator's
 *                       class doc) — not re-clamped or re-derived here.
 * visualFatigue      — the Visual Fatigue input consumed for this
 *                       evaluation (Appendix Q §Q.3), sourced from
 *                       {@link AtmosphereDirector}'s Stage 4 output.
 *                       Already guaranteed within [0,1] by
 *                       {@link DirectorState}'s own validation.
 * peakBoost          — Appendix Q §Q.3: {@code HeroMomentScore × PeakBoostMax},
 *                       before fatigue attenuation.
 * effectivePeakBoost — Appendix Q §Q.4: {@code peakBoost × (1 − visualFatigue)},
 *                       the bonus actually applied to the baseline.
 * value              — Appendix Q §Q.5: {@code Baseline + effectivePeakBoost}.
 *                       The published Global Intensity multiplier.
 *
 * baseline() is a convenience accessor delegating directly to
 * {@link DirectorWeights#GLOBAL_INTENSITY_BASELINE} — deliberately not a
 * stored field, since it is a fixed architectural constant (Appendix Q
 * §Q.3) rather than a per-evaluation intermediate value. Exposing it here
 * saves a future debug overlay from having to reach into
 * {@code DirectorWeights} directly to reconstruct
 * {@code value() - effectivePeakBoost()}.
 */
public record GlobalIntensityResult(
        float heroMomentScore,
        float visualFatigue,
        float peakBoost,
        float effectivePeakBoost,
        float value
) {
    public float baseline() {
        return DirectorWeights.GLOBAL_INTENSITY_BASELINE;
    }
}