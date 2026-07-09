package net.atmos.director;

import net.atmos.confidence.TierAEvaluator;
import net.atmos.confidence.TierAResult;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Atmosphere Director — Chapter 11 Stage 3: Hero Moments & Peak Entry
 * (§11.21-§11.22).
 *
 * Stage 2 implemented Calm/Building/Resolving/Establishing transitions
 * and explicitly capped Tier-A-driven Peak candidacy at BUILDING,
 * deferring true Peak entry "pending Hero Score gating (§11.22), reserved
 * for Stage 3." Stage 3 implements exactly that gating and nothing else.
 *
 * --- Peak entry rule (additive to Stage 2's rule 3 only) ---
 *
 * Stage 2's rule 3 is extended, and only this rule, as follows:
 *
 *     Tier A > PEAK_CANDIDATE_THRESHOLD (0.75)
 *         AND HeroMomentResult.qualifiesForPeak() (§11.22, >0.85)
 *         => PEAK
 *
 *     Tier A > PEAK_CANDIDATE_THRESHOLD, does not qualify
 *         => BUILDING (unchanged Stage 2 behavior)
 *
 * No other Stage 2 rule (biome change, calm floor, rising/falling trend)
 * is modified.
 *
 * --- Evaluation gating ---
 *
 * {@link HeroMomentEvaluator#evaluate} is invoked only inside the
 * Tier A > PEAK_CANDIDATE_THRESHOLD branch — there is no architectural
 * value in scoring Hero Moment quality while the Director is CALM,
 * ESTABLISHING, RESOLVING, or trend-driven BUILDING, since Peak is
 * unreachable in all of those cycles regardless of the score. Every other
 * cycle substitutes {@link HeroMomentResult#EMPTY}.
 *
 * --- Peak exit ---
 *
 * No separate exit rule is introduced; rule 3 re-evaluates every cycle,
 * so Peak persists only while its condition continues to hold. Two
 * decline paths fall out of the existing rule set without new logic:
 *
 *   1. HeroMomentResult no longer qualifies while Tier A remains above
 *      0.75 -> rule 3 now yields BUILDING instead of PEAK. Implementation-
 *      defined reading of §11.17's general "Peak + Conditions Declining
 *      -> Resolving" diagram (Stage 2's approved rule precedence has no
 *      dedicated Peak-to-Resolving edge) — flagged in the delivery
 *      report's Hidden Assumption Audit.
 *   2. Tier A itself falls below PEAK_CANDIDATE_THRESHOLD by more than
 *      TIER_A_TREND_EPSILON -> rule 5 fires -> RESOLVING, matching
 *      §11.17 exactly.
 *
 * --- Ownership, threading ---
 *
 * Unchanged from Stage 1/2: Simulation-Thread-only, no rendering, no
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

        return new DirectorState(currentPhase, previousPhase, transitionReason, timeInPhaseSeconds, heroMoment);
    }

    public void reset() {
        currentPhase          = DirectorPhase.CALM;
        previousPhase         = DirectorPhase.CALM;
        transitionReason      = TransitionReason.NONE;
        timeInPhaseSeconds    = 0f;
        previousBiome         = null;
        previousTierAValue    = 0f;
        hasPreviousTierAValue = false;
    }

    public DirectorPhase currentPhase() {
        return currentPhase;
    }
}