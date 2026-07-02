package net.atmos.confidence;

import net.atmos.atmosphere.EnvironmentalState;

/**
 * Tier A — Atmospheric Possibility (Chapter 4, Part 1 §Tier A).
 *
 * Answers: "Can this phenomenon physically exist right now, ignoring
 * location and camera entirely?" Reads only global EnvironmentalState
 * values — no Cell Grid, no camera, per the Chapter 4 layering rule that
 * Tier A "ignores rendering... ignores camera position... only considers
 * environmental conditions."
 *
 * --- Factor selection (deviation note) ---
 *
 * Chapter 4 §5 lists Tier A inputs as: Humidity Mass, Thermal Energy, Storm
 * Energy, Storm Clearing, Time of Day, Sun Elevation, Weather, Biome
 * Modifier, Season Modifier, Exposure Modifier.
 *
 * Only humidityMass and thermalEnergy are used here. This is a deliberate,
 * documented scope decision, not an oversight:
 *
 *   - Season Modifier, Exposure Modifier, and a standalone Biome Modifier
 *     scalar do not exist anywhere in the codebase yet (no Seasonal Feeling
 *     System, no Exposure Model). Inventing them would violate the "no
 *     placeholder logic" rule.
 *
 *   - Time of Day / Sun Elevation are already folded into thermalEnergy —
 *     EnvironmentalState derives thermalEnergy directly from cos(sunAngle)
 *     (see EnvironmentalState.advance()). Adding a second sun-elevation
 *     factor would double-count the same underlying signal.
 *
 *   - Storm Energy and Storm Clearing were deliberately EXCLUDED after
 *     analysis, not merely deferred. ConfidenceMath's weighted geometric
 *     product treats every factor as a multiplicative gate: a factor value
 *     of 0 forces the entire tier to 0 regardless of weight. stormEnergy is
 *     0 during the vast majority of normal play (no storm active) — wiring
 *     it in as a raw factor would zero Tier A on every clear day, which is
 *     architecturally wrong for a general "atmospheric possibility" gate.
 *     Chapter 11's HeroMomentScore formula treats storm-clearing as a
 *     *bonus multiplier hovering near 1.0*, not a raw 0-anchored gate —
 *     that bonus semantics belongs to the Atmosphere Director (not yet
 *     built), not to Tier A's possibility gate.
 *
 * humidityMass and thermalEnergy remain correct as multiplicative gates:
 * Chapter 4 §5's own worked example ("Night → Sun = 0 → Tier A = 0 →
 * Final = 0") explicitly relies on a sun-elevation-driven factor collapsing
 * to zero at night — exactly what thermalEnergy does here, since
 * EnvironmentalState derives it from max(0, cos(sunAngle)).
 *
 * --- Weight tuning ---
 *
 * Weight values previously lived as local constants in this class. They
 * now live exclusively in ConfidenceWeights (Confidence System Final
 * Cleanup, Task 1) — this class declares no tuning constants of its own.
 * The values themselves are unchanged (a conservative 0.5/0.5 split) and
 * still await Rohit's explicit tuning pass, as previously flagged.
 */
public final class TierAEvaluator {

    private TierAEvaluator() {}

    public static TierAResult evaluate(EnvironmentalState env) {
        // humidityMass and thermalEnergy are already clamped to [0,1] by
        // EnvironmentalState.advance() every tick (via FogMath.clamp calls
        // on the drifters) — no re-clamping needed here, but reading the
        // raw fields directly matches how every existing FogModifier
        // consumes this same state (e.g. HumidityFogModifier).
        float humidity = env.humidityMass;
        float thermal  = env.thermalEnergy;

        // Allocation-free two-factor overload (Confidence System Final
        // Cleanup, Task 2) — no float[] allocated per evaluation.
        float value = ConfidenceMath.weightedGeometricProduct(
                humidity, ConfidenceWeights.TIER_A_WEIGHT_HUMIDITY,
                thermal,  ConfidenceWeights.TIER_A_WEIGHT_THERMAL
        );

        return new TierAResult(humidity, thermal, value);
    }
}