package net.atmos.seasonal;

/**
 * Seasonal Feeling System orchestrator — Chapter 15, Appendix X, extended
 * Phase 1 with the 365-day calendar and influence channels.
 */
public final class SeasonalFeelingSystem {

    private ClimateContext climateContext = ClimateContext.UNINITIALIZED;
    private SeasonalPhaseOffsets phaseOffsets = SeasonalPhaseOffsets.NEUTRAL;
    private long thermalCycleLengthTicks = 0L;
    private boolean configured = false;

    /** Phase 1 — initializes using the 365-day calendar (SFSConstants.YEAR_LENGTH_TICKS). */
    public void initialize(ClimateContext context, long worldSeed) {
        initialize(context, worldSeed, SFSConstants.YEAR_LENGTH_TICKS);
    }

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

        SeasonalCalendarResult calendar = SeasonalCalendar.evaluate(seasonalProgress);
        SeasonalInfluenceResult influence = SeasonalInfluenceEvaluator.evaluate(profile, bias);

        SeasonalFeelingSnapshot snapshot = new SeasonalFeelingSnapshot(
                seasonalProgress,
                mood.macroMood(),
                mood.microMood(),
                profile.thermalTendency(),
                profile.moistureTendency(),
                bias.densityBias(),
                bias.clarityBias(),
                bias.volatility(),
                calendar,
                influence
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