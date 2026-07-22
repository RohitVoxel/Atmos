package net.atmos.director;

/**
 * Immutable output snapshot of one Atmosphere Director update.
 *
 * heroMomentResult      — genuine only when Tier A cleared PEAK_CANDIDATE_THRESHOLD; else EMPTY.
 * visualFatigue         — Stage 4 (§11.25), [0,1].
 * globalIntensityResult — Stage 5 (Appendix Q), always genuine.
 * emotionalRhythm       — Stage 6 (Appendix R), [0,1], independent of the other signals.
 * heroMemory            — Stage 7 (Appendix S) — Recent Hero Moment memory, [0,1].
 *                          Reset on PEAK entry, else decays.
 * performanceState      — Stage 8 (Appendix T) — six budget-driven allocation
 *                          multipliers, always genuine.
 * failureState          — Stage 9 (Appendix U) — weather-stability timer/flag
 *                          and travel scale, always genuine.
 * fogDensity            — Appendix ZC §2 — Director-owned environmental
 *                          visibility signal, [0,1], consumed downstream as
 *                          Appendix ZB Blocker 4's EnvironmentalVisibility.
 *                          Continues updating during weather instability,
 *                          matching Visual Fatigue / Global Intensity /
 *                          Emotional Rhythm / Director Memory's own
 *                          documented behaviour (see AtmosphereDirector).
 */
public record DirectorState(
        DirectorPhase phase,
        DirectorPhase previousPhase,
        TransitionReason transitionReason,
        float timeInPhaseSeconds,
        HeroMomentResult heroMomentResult,
        float visualFatigue,
        GlobalIntensityResult globalIntensityResult,
        float emotionalRhythm,
        float heroMemory,
        DirectorPerformanceState performanceState,
        DirectorFailureState failureState,
        float fogDensity
) {
    public DirectorState {
        if (phase == null) throw new IllegalArgumentException("phase must not be null");
        if (previousPhase == null) throw new IllegalArgumentException("previousPhase must not be null");
        if (transitionReason == null) throw new IllegalArgumentException("transitionReason must not be null");
        if (timeInPhaseSeconds < 0f) {
            throw new IllegalArgumentException("timeInPhaseSeconds must be non-negative, got " + timeInPhaseSeconds);
        }
        if (heroMomentResult == null) throw new IllegalArgumentException("heroMomentResult must not be null");
        if (visualFatigue < 0f || visualFatigue > 1f) {
            throw new IllegalArgumentException("visualFatigue must be within [0,1], got " + visualFatigue);
        }
        if (globalIntensityResult == null) throw new IllegalArgumentException("globalIntensityResult must not be null");
        if (!Float.isFinite(emotionalRhythm) || emotionalRhythm < 0f || emotionalRhythm > 1f) {
            throw new IllegalArgumentException("emotionalRhythm must be within [0,1], got " + emotionalRhythm);
        }
        if (!Float.isFinite(heroMemory) || heroMemory < 0f || heroMemory > 1f) {
            throw new IllegalArgumentException("heroMemory must be within [0,1], got " + heroMemory);
        }
        if (performanceState == null) throw new IllegalArgumentException("performanceState must not be null");
        if (failureState == null) throw new IllegalArgumentException("failureState must not be null");
        if (!Float.isFinite(fogDensity) || fogDensity < 0f || fogDensity > 1f) {
            throw new IllegalArgumentException("fogDensity must be within [0,1], got " + fogDensity);
        }
    }
}