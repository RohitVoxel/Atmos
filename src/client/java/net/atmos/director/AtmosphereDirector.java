package net.atmos.director;

import net.atmos.atmosphere.fog.FogMath;
import net.atmos.confidence.TierAEvaluator;
import net.atmos.confidence.TierAResult;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Atmosphere Director — Chapter 11. Stage 9 (Appendix U) adds Failure
 * Handling: sanitization of raw environmental scalars (§U.15-16), a
 * weather-stability timer that gates Tier A / Hero Moment / phase
 * -transition evaluation while recent rain/thunder readings are still
 * changing (§U.5-9), and a fast-travel scale that accelerates only
 * Director Memory decay (§U.10-14). No Stage 1-8 formula is altered;
 * Stage 9 strictly follows §U.19's mandatory priority order: validate
 * input, stabilize weather, scale travel, then run the unchanged normal
 * update.
 *
 * Stages 1–9 remain unchanged and frozen.
 *
 * Appendix ZC §2 adds fogDensity: a Director-owned, independently
 * evaluated continuous signal (FogDensityEvaluator), computed and
 * published every update regardless of weather-stability gating — the
 * same "continues normally" treatment §U.9 already documents for Visual
 * Fatigue, Global Intensity, Emotional Rhythm, and Director Memory. It
 * is not phase-lock gated because it is not a decision, it is a
 * continuous physical signal.
 *
 * U.6 comparison note: weatherChanged uses Float.compare(a, b) != 0
 * rather than a plain != comparison, per Appendix U §U.6's literal
 * requirement. This is deliberately stricter than IEEE 754 equality
 * (e.g. it treats +0.0f and -0.0f as different, and NaN as equal to
 * itself) — that is Float.compare's defined semantics, not a defect.
 */
public final class AtmosphereDirector {

    private DirectorPhase     currentPhase       = DirectorPhase.CALM;
    private DirectorPhase     previousPhase      = DirectorPhase.CALM;
    private TransitionReason  transitionReason   = TransitionReason.NONE;
    private float             timeInPhaseSeconds = 0f;

    private Holder<Biome> previousBiome         = null;
    private float          previousTierAValue    = 0f;
    private boolean         hasPreviousTierAValue = false;

    private float visualFatigue   = 0f;
    private float emotionalRhythm = 0f;

    /** Stage 7 (§11.28, Appendix S §S.5): Recent Hero Moment memory, [0,1]. */
    private float heroMemory = 0f;

    // --- Stage 9 (Appendix U) ---

    /** §U.6-U.8: seconds of continuously unchanged raw weather. */
    private float weatherStableTime = DirectorWeights.WEATHER_STABILITY_TIME;

    /** §U.12: 0.50 during Fast Travel Mode, else 1.00. */
    private float travelScale = DirectorWeights.NORMAL_TRAVEL_SCALE;

    private Vec3 previousPlayerPosition = null;

    /** §U.15-16: last known-valid readings, substituted on NaN/Infinite input. */
    private float previousValidSunAngleRadians = 0f;
    private float previousValidRainLevel       = 0f;
    private float previousValidThunderLevel    = 0f;
    private float previousValidTierAValue      = 0f;

    public DirectorState update(DirectorInputs inputs, float deltaSec) {
        if (inputs == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        float safeDelta = Math.max(0f, deltaSec);

        // --- Stage 9, Step 1 (§U.19): Input Validation (§U.15-16) ---
        float sunAngleRadians = sanitize(inputs.sunAngleRadians(), previousValidSunAngleRadians);
        previousValidSunAngleRadians = sunAngleRadians;

        float oldRainLevel    = previousValidRainLevel;
        float oldThunderLevel = previousValidThunderLevel;
        float rainLevel    = sanitize(inputs.rainLevel(),    oldRainLevel);
        float thunderLevel = sanitize(inputs.thunderLevel(), oldThunderLevel);
        previousValidRainLevel    = rainLevel;
        previousValidThunderLevel = thunderLevel;

        TierAResult rawTierA = TierAEvaluator.evaluate(inputs.env());
        boolean tierARawValid = Float.isFinite(rawTierA.value());
        float currentTierAValue = tierARawValid ? rawTierA.value() : previousValidTierAValue;
        previousValidTierAValue = currentTierAValue;
        // §U.15: if the raw value was invalid, the sanitized TierAResult
        // fed to HeroMomentEvaluator must carry the substituted value too
        // — otherwise tierA.value() re-read inside that evaluator would
        // re-introduce the same NaN/Infinite this step exists to remove.
        TierAResult tierA = tierARawValid
                ? rawTierA
                : new TierAResult(rawTierA.humidityFactor(), rawTierA.thermalFactor(), currentTierAValue);

        // --- Stage 9, Step 2 (§U.5-9): Weather Stabilization ---
        // §U.6: Float.compare(a,b) != 0 — not a plain != comparison.
        boolean weatherChanged =
                Float.compare(rainLevel, oldRainLevel) != 0
                        || Float.compare(thunderLevel, oldThunderLevel) != 0;
        if (weatherChanged) {
            weatherStableTime = 0f;
        } else {
            weatherStableTime = Math.min(
                    weatherStableTime + safeDelta, DirectorWeights.WEATHER_STABILITY_TIME);
        }
        boolean weatherStable = weatherStableTime >= DirectorWeights.WEATHER_STABILITY_TIME;

        // --- Stage 9, Step 3 (§U.10-14): Fast Travel Scaling ---
        Vec3 playerPosition = inputs.playerPosition();
        if (playerPosition != null
                && Double.isFinite(playerPosition.x())
                && Double.isFinite(playerPosition.y())
                && Double.isFinite(playerPosition.z())) {

            if (previousPlayerPosition != null && safeDelta > 0f) {
                double dx = playerPosition.x() - previousPlayerPosition.x();
                double dz = playerPosition.z() - previousPlayerPosition.z();
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                float  speed = (float) (horizontalDistance / safeDelta);

                if (Float.isFinite(speed)) {
                    travelScale = speed > DirectorWeights.FAST_TRAVEL_SPEED
                            ? DirectorWeights.FAST_TRAVEL_SCALE
                            : DirectorWeights.NORMAL_TRAVEL_SCALE;
                }
            }
            previousPlayerPosition = playerPosition;
        }
        // else: missing or non-finite input (§U.17) — travelScale and
        // previousPlayerPosition are left unchanged.

        // --- Stage 9, Step 4 (§U.19): Normal Director Update (Stages 1-8, unchanged) ---
        Holder<Biome> biome = inputs.biome();
        boolean biomeChanged = (previousBiome == null) || !previousBiome.equals(biome);

        DirectorPhase newPhase;
        TransitionReason reason;
        HeroMomentResult heroMoment = HeroMomentResult.EMPTY;

        if (biomeChanged) {
            newPhase = DirectorPhase.ESTABLISHING;
            reason   = TransitionReason.BIOME_CHANGE;
        } else if (!weatherStable) {
            // §U.9 — unstable weather shall not influence Tier A, Hero
            // Moment, or phase transitions; the current phase is held.
            newPhase = currentPhase;
            reason   = TransitionReason.NONE;
        } else if (currentTierAValue < DirectorWeights.CALM_THRESHOLD) {
            newPhase = DirectorPhase.CALM;
            reason   = TransitionReason.TIER_A_BELOW_CALM_THRESHOLD;
        } else if (currentTierAValue > DirectorWeights.PEAK_CANDIDATE_THRESHOLD) {
            heroMoment = HeroMomentEvaluator.evaluate(tierA, inputs.env(), sunAngleRadians);

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

        boolean phaseChanged = newPhase != currentPhase;

        if (phaseChanged) {
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

        // --- Stage 5: Global Intensity (§11.26, Appendix Q) ---
        GlobalIntensityResult globalIntensity =
                GlobalIntensityEvaluator.evaluate(heroMoment, visualFatigue);

        // --- Stage 6: Emotional Rhythm (§11.27, Appendix R §R.14) ---
        float rhythmTarget = DirectorWeights.emotionalRhythmTarget(currentPhase);
        emotionalRhythm = FogMath.clamp(
                emotionalRhythm
                        + (rhythmTarget - emotionalRhythm) * DirectorWeights.EMOTIONAL_RHYTHM_SPEED * safeDelta,
                0f, 1f);

        // --- Stage 7: Director Memory (§11.28, Appendix S §S.6/§S.7) ---
        // Decay rate scaled by travelScale per §U.13 — the only Stage 9
        // effect permitted on Director Memory mathematics (§U.23).
        if (phaseChanged && reason == TransitionReason.HERO_MOMENT_PEAK_ENTERED) {
            heroMemory = 1f;
        } else {
            float effectiveDecayRate = DirectorWeights.HERO_MEMORY_DECAY_RATE / travelScale;
            heroMemory = FogMath.clamp(
                    heroMemory - effectiveDecayRate * safeDelta,
                    0f, 1f);
        }

        // --- Stage 8: Adaptive Performance Integration (§11.32-33, Appendix T) ---
        DirectorPerformanceState performanceState =
                DirectorPerformanceEvaluator.evaluate(inputs.optimizationPlan());

        // --- Stage 9: Failure Handling State (Appendix U) ---
        DirectorFailureState failureState =
                new DirectorFailureState(weatherStableTime, weatherStable, travelScale);

        // --- Appendix ZC §2: Director-owned fogDensity ---
        // Computed unconditionally, independent of weatherStable — see
        // class doc for why this is not phase-lock gated.
        float fogDensity = FogDensityEvaluator.evaluate(inputs.env());

        return new DirectorState(
                currentPhase, previousPhase, transitionReason, timeInPhaseSeconds,
                heroMoment, visualFatigue, globalIntensity, emotionalRhythm, heroMemory,
                performanceState, failureState, fogDensity);
    }

    /** §U.15 — returns {@code value} if finite, else {@code previousValid}. */
    private static float sanitize(float value, float previousValid) {
        return Float.isFinite(value) ? value : previousValid;
    }

    /**
     * Stage 9 fields reset per §U.24: weatherStableTime starts already
     * stable and travelScale starts normal, avoiding a spurious startup
     * delay.
     */
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
        heroMemory            = 0f;

        weatherStableTime            = DirectorWeights.WEATHER_STABILITY_TIME;
        travelScale                  = DirectorWeights.NORMAL_TRAVEL_SCALE;
        previousPlayerPosition       = null;
        previousValidSunAngleRadians = 0f;
        previousValidRainLevel       = 0f;
        previousValidThunderLevel    = 0f;
        previousValidTierAValue      = 0f;
    }

    public DirectorPhase currentPhase()   { return currentPhase;   }
    public float visualFatigue()          { return visualFatigue;  }
    public float emotionalRhythm()        { return emotionalRhythm; }
    public float heroMemory()             { return heroMemory;    }
    public float weatherStableTime()      { return weatherStableTime; }
    public boolean weatherStable()        { return weatherStableTime >= DirectorWeights.WEATHER_STABILITY_TIME; }
    public float travelScale()            { return travelScale; }
}