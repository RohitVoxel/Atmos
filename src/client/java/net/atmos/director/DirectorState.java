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
 *
 * --- visualFatigue (Stage 4, Chapter 11 §11.24-§11.25) ---
 *
 * Continuous [0,1] signal tracking accumulated atmospheric intensity per
 * §11.24 ("Visual Fatigue... increases whenever the atmosphere remains
 * visually intense for a long time"). Accumulation and recovery rates are
 * anchored only for PEAK (accumulates) and CALM (recovers) per §11.25 —
 * see {@link AtmosphereDirector}'s class doc for the implementation-defined
 * treatment of the remaining three phases.
 *
 * [0,1] bound: not explicitly stated as a range anywhere in Chapter 11,
 * but adopted here for consistency with every other normalized Atmos
 * signal (Confidence, HeroScore, etc.) — flagged as an implementation
 * decision, not an invented rate.
 *
 * --- globalIntensityResult (Stage 5, Chapter 11 §11.26, Appendix Q) ---
 *
 * The Director's single master atmospheric multiplier, evaluated every
 * cycle by {@link GlobalIntensityEvaluator} from this same cycle's
 * {@code heroMomentResult} and {@code visualFatigue} per Appendix Q's
 * canonical formula. Unlike {@code heroMomentResult}, this is never an
 * EMPTY sentinel — it is a genuine evaluation on every cycle, since
 * Appendix Q's formula is well-defined even when {@code heroMomentResult}
 * is {@link HeroMomentResult#EMPTY} (the result simply collapses to
 * {@link DirectorWeights#GLOBAL_INTENSITY_BASELINE}).
 *
 * This field is published in this stage only. Per Appendix Q §Q.8,
 * GlobalIntensity is read-only for downstream consumers; no fog, mist,
 * crepuscular ray, exposure, or ambient density system yet reads it —
 * that integration is explicitly deferred to a future stage.
 *
 * --- emotionalRhythm (Stage 6, Chapter 11 §11.27, Appendix R) ---
 *
 * Continuous [0,1] signal tracking the Director's slow, long-term
 * atmospheric "breathing" per §11.27 and Appendix R's canonical
 * first-order convergence formula (R.14):
 *
 *     emotionalRhythm += (phaseTarget(currentPhase) - emotionalRhythm)
 *                         × EMOTIONAL_RHYTHM_SPEED × deltaSec
 *
 * Unlike {@code visualFatigue}, every {@link DirectorPhase} carries an
 * anchored target per Appendix R §R.5, so this value is a genuine
 * evaluation on every cycle regardless of phase — there is no EMPTY or
 * "held constant" case.
 *
 * Per Appendix R §R.13, this signal is intentionally independent of
 * {@code heroMomentResult}, {@code visualFatigue}, and
 * {@code globalIntensityResult} — it neither reads nor influences them.
 *
 * Per Appendix R §R.9, this field is published in this stage only. No
 * consumer reads it yet — matching the identical precedent Appendix R
 * itself draws to {@code visualFatigue}'s state prior to Stage 5.
 */
public record DirectorState(
        DirectorPhase phase,
        DirectorPhase previousPhase,
        TransitionReason transitionReason,
        float timeInPhaseSeconds,
        HeroMomentResult heroMomentResult,
        float visualFatigue,
        GlobalIntensityResult globalIntensityResult,
        float emotionalRhythm
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
        if (visualFatigue < 0f || visualFatigue > 1f) {
            throw new IllegalArgumentException(
                    "visualFatigue must be within [0,1], got " + visualFatigue);
        }
        if (globalIntensityResult == null) {
            throw new IllegalArgumentException("globalIntensityResult must not be null");
        }
        if (!Float.isFinite(emotionalRhythm)) {
            throw new IllegalArgumentException(
                    "emotionalRhythm must be finite, got " + emotionalRhythm);
        }
        if (emotionalRhythm < 0f || emotionalRhythm > 1f) {
            throw new IllegalArgumentException(
                    "emotionalRhythm must be within [0,1], got " + emotionalRhythm);
        }
    }
}