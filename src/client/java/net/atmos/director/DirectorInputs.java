package net.atmos.director;

import net.atmos.aps.OptimizationPlan;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.composition.Composition;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable bundle of everything one Atmosphere Director evaluation
 * consumes, per Chapter 11 §11.6 ("Inputs").
 *
 * env         — current {@link EnvironmentalState}.
 * composition — current cycle's {@link Composition}.
 * biome       — current dominant biome, per §11.18's biome-change rule.
 * sunAngleRadians — current sun angle, per §11.22's GoldenHourBonus.
 * optimizationPlan — current Adaptive Performance budget (Appendix T §T.6).
 *                     {@code null} is a valid, architecturally-defined state.
 *
 * --- Stage 9 additions (Appendix U) ---
 *
 * rainLevel, thunderLevel — raw, unsmoothed world weather levels (the
 * same source FogContext's raw capture reads, e.g.
 * level.getRainLevel(1.0f) / level.getThunderLevel(1.0f)). Deliberately
 * unsmoothed: §U.5's weather-stability timer must observe genuine abrupt
 * changes, not FogManager's already-smoothed values — reusing the
 * smoothed values here would make instability undetectable. Non-finite
 * readings are tolerated here and sanitized by the Director itself
 * (§U.15) rather than rejected at construction, since rejecting them
 * would make that resilience requirement unreachable.
 *
 * playerPosition — current world-space player position, consumed only by
 * §U.10's raw horizontal-speed computation. The Director computes this
 * speed itself from raw position deltas rather than reusing
 * FogContext.getSmoothedSpeed() — §U.25 explicitly prohibits consuming
 * an already-smoothed player speed for Stage 9. Nullable: per §U.17, a
 * missing snapshot is a valid "optional input unavailable" state that
 * the Director tolerates by reusing its last known travel scale.
 *
 * Rain History, Altitude, smoothed Player Speed, Travel Direction,
 * Recent Hero Moments, Visual Fatigue, Atmospheric Memory, and Exposure
 * remain out of scope, unchanged from prior stages.
 */
public record DirectorInputs(
        EnvironmentalState env,
        Composition composition,
        Holder<Biome> biome,
        float sunAngleRadians,
        OptimizationPlan optimizationPlan,
        float rainLevel,
        float thunderLevel,
        Vec3 playerPosition
) {
    public DirectorInputs {
        if (env == null) {
            throw new IllegalArgumentException("env must not be null");
        }
        if (composition == null) {
            throw new IllegalArgumentException("composition must not be null");
        }
        if (biome == null) {
            throw new IllegalArgumentException("biome must not be null");
        }
        // sunAngleRadians, rainLevel, thunderLevel: no finiteness check —
        // Appendix U §U.15 requires the Director to tolerate and sanitize
        // non-finite readings rather than have them rejected upstream.
        // optimizationPlan, playerPosition: no null check — see class doc.
    }
}