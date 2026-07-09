package net.atmos.director;

/**
 * Immutable output snapshot of one Atmosphere Director update, per
 * Chapter 11 §11.6-§11.7 and §11.19-§11.20.
 *
 * previousPhase / transitionReason — sticky across cycles where phase
 * does not change (§11.19 "Transition Memory").
 *
 * --- heroMomentResult (Stage 3) ---
 *
 * Per Issue 2's gating: this is a genuine {@link HeroMomentEvaluator}
 * output only on cycles where Tier A exceeded
 * {@link DirectorWeights#PEAK_CANDIDATE_THRESHOLD} (i.e. the cycle either
 * entered PEAK or was capped at BUILDING). On every other cycle (CALM,
 * ESTABLISHING, RESOLVING, trend-driven BUILDING) this is
 * {@link HeroMomentResult#EMPTY} — evaluation was architecturally skipped,
 * not performed and found lacking. Consumers must check the current
 * {@code phase}/{@code transitionReason} before treating this field as a
 * meaningful score.
 *
 * "Transition Progress" (§11.19) remains omitted — it depends on Chapter
 * 5's Atmospheric Transition State, which does not exist.
 */
public record DirectorState(
        DirectorPhase phase,
        DirectorPhase previousPhase,
        TransitionReason transitionReason,
        float timeInPhaseSeconds,
        HeroMomentResult heroMomentResult
) {
    public DirectorState {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        if (previousPhase == null) {
            throw new IllegalArgumentException("previousPhase must not be null");
        }
        if (transitionReason == null) {
            throw new IllegalArgumentException("transitionReason must not be null");
        }
        if (timeInPhaseSeconds < 0f) {
            throw new IllegalArgumentException(
                    "timeInPhaseSeconds must be non-negative, got " + timeInPhaseSeconds);
        }
        if (heroMomentResult == null) {
            throw new IllegalArgumentException("heroMomentResult must not be null");
        }
    }
}