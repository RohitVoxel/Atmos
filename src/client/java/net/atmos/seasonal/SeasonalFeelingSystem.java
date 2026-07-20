package net.atmos.seasonal;

/**
 * Seasonal Feeling System orchestrator — Chapter 15, Appendix X Revision
 * 2.7 (Stage 2 Seasonal Clock / Daily Rhythm / Atmospheric Mood, plus
 * Stage 3/4 Seed Hash Utility, Seasonal Profile Model, Continuous Bias
 * Generation).
 *
 * initialize() now takes worldSeed and thermalCycleLengthTicks directly
 * (review correction, points 2 and 4):
 *
 *   - worldSeed is never stored on ClimateContext (see that class's doc);
 *     it is consumed once, here, to derive this session's
 *     SeasonalPhaseOffsets via SeasonalSeedHash, then discarded.
 *   - thermalCycleLengthTicks is resolved once per session rather than
 *     threaded through update() every tick. No live Configuration
 *     Manager class exists yet in this codebase (Rev 2.7 §4's
 *     "Configuration Manager -> Seasonal Clock" chain), so this value is
 *     still supplied by the caller rather than invented — but it is now
 *     confined to one-time session setup, matching the architectural
 *     intent that this is a semi-static configuration value, not a
 *     per-tick input. update() has reverted to its original single-
 *     argument signature.
 *
 * Pipeline per tick (feed-forward, no ownership overlap):
 *
 *     SeasonalClock.progress(thermal)   -> seasonalProgress / thermalProgress
 *     SeasonalClock.deriveMoistureCycleLength + progress(moisture) -> moistureProgress
 *     SeasonalProfileModel.evaluate(thermalProgress, moistureProgress, offsets)
 *         -> thermalTendency, moistureTendency
 *     ContinuousBiasGenerator.evaluate(thermalTendency, moistureTendency)
 *         -> densityBias, clarityBias, volatility
 *
 * seasonalProgress and thermalProgress are the same value (both are
 * SeasonalClock.progress against thermalCycleLengthTicks) — computed
 * once and reused, not duplicated.
 *
 * Non-Overworld dimensions (ClimateContext.seasonalCycleSupported() ==
 * false) continue to publish a neutral snapshot, per Appendix X §14/§31.
 */
public final class SeasonalFeelingSystem {

    private ClimateContext climateContext = ClimateContext.UNINITIALIZED;
    private SeasonalPhaseOffsets phaseOffsets = SeasonalPhaseOffsets.NEUTRAL;
    private long thermalCycleLengthTicks = 0L;
    private boolean configured = false;

    /**
     * @param context                 dimension characteristics, per Appendix X §10.
     * @param worldSeed                consumed once here to derive phase offsets;
     *                                 never stored — see class doc.
     * @param thermalCycleLengthTicks resolved once per session; still an
     *                                 Architect-supplied value, not invented — see
     *                                 class doc.
     */
    public void initialize(ClimateContext context, long worldSeed, long thermalCycleLengthTicks) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (thermalCycleLengthTicks <= 0L) {
            throw new IllegalArgumentException(
                    "thermalCycleLengthTicks must be positive, got " + thermalCycleLengthTicks);
        }

        this.climateContext = context;
        this.phaseOffsets = SeasonalSeedHash.derive(worldSeed);
        this.thermalCycleLengthTicks = thermalCycleLengthTicks;
        this.configured = true;

        SeasonalFeelingStateManager.publish(SeasonalFeelingSnapshot.neutral());
    }

    /** Stage 2 + Stage 3/4 tick. */
    public void update(long worldTimeTicks) {
        if (!configured || !climateContext.seasonalCycleSupported()) {
            SeasonalFeelingStateManager.publish(SeasonalFeelingSnapshot.neutral());
            return;
        }

        float seasonalProgress = SeasonalClock.progress(worldTimeTicks, thermalCycleLengthTicks);
        float dailyProgress    = DailyRhythm.progress(worldTimeTicks);

        AtmosphericMoodResult mood = AtmosphericMood.evaluate(seasonalProgress, dailyProgress);

        long moistureCycleLengthTicks = SeasonalClock.deriveMoistureCycleLength(thermalCycleLengthTicks);
        float moistureProgress = SeasonalClock.progress(worldTimeTicks, moistureCycleLengthTicks);

        SeasonalProfileResult profile = SeasonalProfileModel.evaluate(
                seasonalProgress,
                moistureProgress,
                phaseOffsets.thermalPhaseOffsetRadians(),
                phaseOffsets.moisturePhaseOffsetRadians());

        SeasonalBiasResult bias = ContinuousBiasGenerator.evaluate(
                profile.thermalTendency(),
                profile.moistureTendency());

        SeasonalFeelingSnapshot snapshot = new SeasonalFeelingSnapshot(
                seasonalProgress,
                mood.macroMood(),
                mood.microMood(),
                profile.thermalTendency(),
                profile.moistureTendency(),
                bias.densityBias(),
                bias.clarityBias(),
                bias.volatility()
        );

        SeasonalFeelingStateManager.publish(snapshot);
    }

    public ClimateContext climateContext() {
        return climateContext;
    }

    public void reset() {
        climateContext = ClimateContext.UNINITIALIZED;
        phaseOffsets = SeasonalPhaseOffsets.NEUTRAL;
        thermalCycleLengthTicks = 0L;
        configured = false;
        SeasonalFeelingStateManager.reset();
    }
}