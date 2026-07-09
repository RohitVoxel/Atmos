package net.atmos.director;

/**
 * Explainable breakdown of one Hero Moment Score evaluation, per Chapter 11
 * §11.21–§11.22 ("Hero Moment Score") and Appendix C §4 (term disambiguation:
 * "HeroMomentScore... the score used to determine if the global atmospheric
 * conditions warrant a cinematic 'Hero Moment' state").
 *
 * --- Scope reduction (Stage 3) ---
 *
 * Appendix C §4 defines the canonical formula as:
 *
 *     HeroMomentScore = TierA x StormClearingBonus x GoldenHourBonus
 *                       x HumidityBonus x CompositionScore x ExposureScore
 *                       x MemoryBonus
 *
 * Three of these seven factors have no architectural data source yet and
 * are intentionally omitted from the product entirely (not fixed at 1.0,
 * not approximated) — the identical precedent already established by
 * {@link net.atmos.composition.CompositionWeights}'s own Hero Score scope
 * reduction:
 *
 *   CompositionScore — no numeric "composition quality" score is produced
 *       anywhere. Even HeroScoreResult (the closest analog) is discarded
 *       after Hero selection and never stored on
 *       {@link net.atmos.composition.Composition}.
 *   ExposureScore — the Exposure Model (Chapter 14) is not implemented.
 *   MemoryBonus — Atmospheric Memory (Chapter 13) is not implemented.
 *
 * Fields:
 *
 *   tierAFactor         — {@link net.atmos.confidence.TierAResult#value()}.
 *   stormClearingFactor — {@link net.atmos.atmosphere.EnvironmentalState#getStormClearing()}.
 *   goldenHourFactor    — {@code FogMath.horizonFactor} (shared identically by
 *                         DaylightFogModifier, SkyColorController, FogMixin).
 *   humidityFactor      — {@link net.atmos.atmosphere.EnvironmentalState#getHumidityMass()},
 *                         identity mapping (no numeric anchors exist for this
 *                         stage, same precedent as HumidityInteractionEvaluator).
 *   value               — product of the four factors above.
 *
 * --- Evaluation gating (Stage 3 revision) ---
 *
 * Hero Moment scoring exists solely to gate PEAK entry (§11.22). Per
 * §11.21, "Hero Moments... naturally emerge when multiple atmospheric
 * systems align" — the Director therefore only evaluates a real Hero
 * Moment score when Tier A has already cleared
 * {@link DirectorWeights#PEAK_CANDIDATE_THRESHOLD}, since Peak is
 * architecturally unreachable below that threshold regardless of what a
 * Hero Moment score would have been. {@link #EMPTY} is substituted on
 * every other cycle instead.
 */
public record HeroMomentResult(
        float tierAFactor,
        float stormClearingFactor,
        float goldenHourFactor,
        float humidityFactor,
        float value
) {

    /**
     * Sentinel representing "not evaluated this cycle."
     *
     * {@link AtmosphereDirector} substitutes this constant, rather than
     * calling {@link HeroMomentEvaluator#evaluate}, whenever Tier A does
     * not exceed {@link DirectorWeights#PEAK_CANDIDATE_THRESHOLD} — CALM,
     * ESTABLISHING, RESOLVING, and BUILDING-via-trend-rule cycles all
     * receive this value.
     *
     * {@code EMPTY.value()} is {@code 0f}, which also satisfies
     * {@link #qualifiesForPeak()} as {@code false} — but callers must not
     * read EMPTY as "evaluated and scored zero." It means evaluation was
     * architecturally skipped because Peak candidacy was never possible
     * this cycle in the first place.
     */
    public static final HeroMomentResult EMPTY = new HeroMomentResult(0f, 0f, 0f, 0f, 0f);

    /**
     * Chapter 11 §11.22 — "If HeroScore > 0.85 the Director enters Hero
     * Mode." Centralizes the threshold comparison against
     * {@link DirectorWeights#HERO_MOMENT_THRESHOLD} so consumers never
     * duplicate {@code value() > threshold} logic, and so a future
     * threshold change requires updating only {@link DirectorWeights}.
     */
    public boolean qualifiesForPeak() {
        return value > DirectorWeights.HERO_MOMENT_THRESHOLD;
    }
}