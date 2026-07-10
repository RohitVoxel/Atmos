package net.atmos.director;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.confidence.TierAEvaluator;
import net.atmos.confidence.TierAResult;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Atmosphere Director — Chapter 11 Stage 6: Emotional Rhythm (§11.27,
 * Appendix R).
 *
 * Stage 5 implemented the Global Intensity Controller (§11.26, Appendix Q).
 * Stage 6 implements exactly the Emotional Rhythm signal described by
 * §11.27, mathematically specified in full by Appendix R, and nothing
 * else. §11.28 onward (Director Memory) remains explicitly out of scope.
 *
 * --- Visual Fatigue accumulation rule (§11.25), unchanged from Stage 4 ---
 *
 * §11.25 anchors exactly two rates:
 *
 *     PEAK phase this cycle  -> fatigue += 0.003 * deltaSec
 *     CALM phase this cycle  -> fatigue -= 0.001 * deltaSec
 *
 * No rate is anchored anywhere in Chapter 11 for BUILDING, RESOLVING, or
 * ESTABLISHING. This implementation holds fatigue constant during those
 * three phases — the only reading that adds zero unanchored numeric
 * behavior. This remains flagged as an implementation decision in the
 * Hidden Assumption Audit, not asserted as settled architecture.
 *
 * Fatigue is evaluated against {@code currentPhase} — the phase already
 * committed for this update cycle (after any transition in this same
 * call) — matching how {@code timeInPhaseSeconds} is already accumulated
 * against the same post-transition {@code currentPhase} value.
 *
 * --- Stage 5: Global Intensity (§11.26, Appendix Q), unchanged ---
 *
 * Every update cycle evaluates the Director's single master atmospheric
 * multiplier via {@link GlobalIntensityEvaluator}, using this same
 * cycle's Hero Moment Score ({@code heroMoment.value()}) and Visual
 * Fatigue as inputs, per Appendix Q §Q.5's canonical formula:
 *
 *     GlobalIntensity = Baseline + (HeroMomentScore × 0.12 × (1 − VisualFatigue))
 *
 * --- Stage 6: Emotional Rhythm (§11.27, Appendix R) ---
 *
 * Every update cycle also advances the Director's Emotional Rhythm
 * signal — a slow, continuous first-order convergence toward a
 * phase-dependent target, per Appendix R §R.14's canonical formula:
 *
 *     target = phaseTarget(currentPhase)
 *     emotionalRhythm += (target - emotionalRhythm) × EMOTIONAL_RHYTHM_SPEED × deltaSec
 *     emotionalRhythm = clamp(emotionalRhythm, 0, 1)
 *
 * The phase→target lookup is centralized in
 * {@link DirectorWeights#emotionalRhythmTarget(DirectorPhase)} rather
 * than embedded here, mirroring the ConfidenceWeights / ClusterConstants
 * / CompositionWeights pattern of keeping tuning data in one place.
 *
 * Unlike Visual Fatigue, Appendix R §R.5 anchors a target for all five
 * {@link DirectorPhase} values, so this computation runs unconditionally
 * every cycle — no phase is left as an implementation-defined "hold."
 *
 * Per Appendix R §R.13, Emotional Rhythm is intentionally independent of
 * Hero Moment, Visual Fatigue, and Global Intensity — it reads only
 * {@code currentPhase} and {@code deltaSec}, and its own previous value.
 * It introduces no new persistent state consumed by those other systems,
 * and consumes none of theirs.
 *
 * Per Appendix R §R.9, this stage only publishes the signal on
 * {@link DirectorState}. No consumer is wired in this task — matching
 * the explicit precedent Appendix R draws to Visual Fatigue's own
 * pre-Stage-5 unconsumed state.
 *
 * --- Explicitly out of scope for Stage 6 ---
 *
 * §11.28 (Director Memory) remains unimplemented. No consumer of
 * Emotional Rhythm — Fog, Mist, Crepuscular Rays, Exposure, Ambient
 * Density, Composition — is wired in this task.
 *
 * --- Ownership, threading ---
 *
 * Unchanged from Stage 1–5: Simulation-Thread-only, no rendering, no
 * mutation of EnvironmentalState or Composition, not wired into any
 * render-event hook or AtmosClient lifecycle.
 */
public final class AtmosphereDirector {

    private DirectorPhase     currentPhase       = DirectorPhase.CALM;
    private DirectorPhase     previousPhase      = DirectorPhase.CALM;
    private TransitionReason  transitionReason   = TransitionReason.NONE;
    private float             timeInPhaseSeconds = 0f;

    private Holder<Biome> previousBiome         = null;
    private float          previousTierAValue    = 0f;
    private boolean         hasPreviousTierAValue = false;

    // Stage 4 (§11.24-§11.25): accumulated visual fatigue, [0,1].
    private float visualFatigue = 0f;

    // Stage 6 (§11.27, Appendix R §R.3): accumulated emotional rhythm, [0,1].
    // Initial value 0.0 per Appendix R §R.3.
    private float emotionalRhythm = 0f;

    public DirectorState update(DirectorInputs inputs, float deltaSec) {
        if (inputs == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        float safeDelta = Math.max(0f, deltaSec);

        TierAResult tierA = TierAEvaluator.evaluate(inputs.env());
        float currentTierAValue = tierA.value();

        Holder<Biome> biome = inputs.biome();
        boolean biomeChanged = (previousBiome == null) || !previousBiome.equals(biome);

        DirectorPhase newPhase;
        TransitionReason reason;
        HeroMomentResult heroMoment = HeroMomentResult.EMPTY;

        if (biomeChanged) {
            newPhase = DirectorPhase.ESTABLISHING;
            reason   = TransitionReason.BIOME_CHANGE;
        } else if (currentTierAValue < DirectorWeights.CALM_THRESHOLD) {
            newPhase = DirectorPhase.CALM;
            reason   = TransitionReason.TIER_A_BELOW_CALM_THRESHOLD;
        } else if (currentTierAValue > DirectorWeights.PEAK_CANDIDATE_THRESHOLD) {
            // Hero Moment evaluation only ever matters here — Peak is
            // architecturally unreachable outside this branch (Issue 2).
            heroMoment = HeroMomentEvaluator.evaluate(tierA, inputs.env(), inputs.sunAngleRadians());

            if (heroMoment.qualifiesForPeak()) {
                newPhase = DirectorPhase.PEAK;
                reason   = TransitionReason.HERO_MOMENT_PEAK_ENTERED;
            } else {
                newPhase = DirectorPhase.BUILDING;
                reason   = TransitionReason.TIER_A_ABOVE_PEAK_CANDIDATE_THRESHOLD;
            }
        } else if (hasPreviousTierAValue
                && (currentTierAValue - previousTierAValue) > DirectorWeights.TIER_A_TREND_EPSILON) {
            newPhase = DirectorPhase.BUILDING;
            reason   = TransitionReason.TIER_A_RISING;
        } else if (hasPreviousTierAValue
                && (previousTierAValue - currentTierAValue) > DirectorWeights.TIER_A_TREND_EPSILON) {
            newPhase = DirectorPhase.RESOLVING;
            reason   = TransitionReason.TIER_A_FALLING;
        } else {
            newPhase = currentPhase;
            reason   = TransitionReason.NONE;
        }

        previousTierAValue    = currentTierAValue;
        hasPreviousTierAValue = true;
        previousBiome         = biome;

        if (newPhase != currentPhase) {
            previousPhase      = currentPhase;
            currentPhase       = newPhase;
            transitionReason   = reason;
            timeInPhaseSeconds = 0f;
        } else {
            timeInPhaseSeconds += safeDelta;
        }

        // --- Stage 4: Visual Fatigue (§11.25) ---
        if (currentPhase == DirectorPhase.PEAK) {
            visualFatigue = FogMath.clamp(
                    visualFatigue + DirectorWeights.VISUAL_FATIGUE_PEAK_INCREASE_RATE * safeDelta,
                    0f, 1f);
        } else if (currentPhase == DirectorPhase.CALM) {
            visualFatigue = FogMath.clamp(
                    visualFatigue - DirectorWeights.VISUAL_FATIGUE_CALM_DECREASE_RATE * safeDelta,
                    0f, 1f);
        }

        // --- Stage 5: Global Intensity Controller (§11.26, Appendix Q) ---
        // Pure function of this cycle's Hero Moment Score and Visual
        // Fatigue — no persistent state, always evaluated, never gated.
        // heroMoment passed as a domain object; the evaluator owns
        // extraction of heroMoment.value() internally.
        GlobalIntensityResult globalIntensity =
                GlobalIntensityEvaluator.evaluate(heroMoment, visualFatigue);

        // --- Stage 6: Emotional Rhythm (§11.27, Appendix R §R.14) ---
        // Unconditional first-order convergence toward the current
        // phase's target — every DirectorPhase has an anchored target
        // per Appendix R §R.5, so no phase is skipped or held constant.
        // Independent of Hero Moment, Visual Fatigue, and Global
        // Intensity per Appendix R §R.13.
        float rhythmTarget = DirectorWeights.emotionalRhythmTarget(currentPhase);
        emotionalRhythm = FogMath.clamp(
                emotionalRhythm
                        + (rhythmTarget - emotionalRhythm) * DirectorWeights.EMOTIONAL_RHYTHM_SPEED * safeDelta,
                0f, 1f);

        return new DirectorState(
                currentPhase, previousPhase, transitionReason, timeInPhaseSeconds,
                heroMoment, visualFatigue, globalIntensity, emotionalRhythm);
    }

    public void reset() {
        currentPhase          = DirectorPhase.CALM;
        previousPhase         = DirectorPhase.CALM;
        transitionReason      = TransitionReason.NONE;
        timeInPhaseSeconds    = 0f;
        previousBiome         = null;
        previousTierAValue    = 0f;
        hasPreviousTierAValue = false;
        visualFatigue         = 0f;
        emotionalRhythm       = 0f;
    }

    public DirectorPhase currentPhase() {
        return currentPhase;
    }

    /** Stage 4 (§11.24-§11.25) — current accumulated Visual Fatigue, [0,1]. */
    public float visualFatigue() {
        return visualFatigue;
    }

    /** Stage 6 (§11.27, Appendix R) — current Emotional Rhythm value, [0,1]. */
    public float emotionalRhythm() {
        return emotionalRhythm;
    }
}