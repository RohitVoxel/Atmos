package net.atmos.render;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;
import net.atmos.cluster.Cluster;

/**
 * Animation Phase Producer — Appendix ZB Blocker 5, Appendix ZD §3.
 *
 * sigma resolution per Appendix ZD §3:
 *
 *     Cluster.anchorCoord() -> CellGrid.getCell() -> AtmosCell.deterministicSeed()
 *
 * On eviction (getCell() returns null), sigma falls back to the neutral
 * seed 0, preserving Chapter 6 cache behaviour without querying an inactive
 * cell. The raw long seed is mapped to a bounded radian offset via the
 * existing package-local {@link SeedHash} utility (already used by
 * RendererExpansion / DensityProbabilityMap for identical deterministic
 * seed-to-float conversion) — no new hashing system is introduced.
 *
 * omega/Phi_anim follow Appendix ZB Blocker 5 §4 exactly, using
 * RenderingMathConstants.ANIMATION_BASE_SPEED / ANIMATION_STORM_MULTIPLIER.
 * The floor-based modulo guarantees 0 &lt;= Phi_anim &lt; 2*PI per Blocker 5 §5.
 *
 * Stateless, deterministic, O(1) — never reads Math.random().
 */
public final class AnimationPhaseEvaluator {

    private AnimationPhaseEvaluator() {}

    private static final float TWO_PI = (float) (2.0 * Math.PI);
    private static final long  NEUTRAL_SEED = 0L;

    public static AnimationPhaseResult evaluate(
            float gameTimeSeconds, EnvironmentalState env, Cluster cluster, CellGrid cellGrid) {

        long deterministicSeed = resolveSeed(cluster, cellGrid);
        float sigma = SeedHash.toUnitFloat(SeedHash.mix(deterministicSeed)) * TWO_PI;

        float stormEnergy = env.getStormEnergy();
        float omega = FogMath.lerp(
                RenderingMathConstants.ANIMATION_BASE_SPEED,
                RenderingMathConstants.ANIMATION_STORM_MULTIPLIER,
                stormEnergy);

        float rawPhase = gameTimeSeconds * omega + sigma;
        float phi = rawPhase - (float) Math.floor(rawPhase / TWO_PI) * TWO_PI;

        return new AnimationPhaseResult(sigma, omega, phi);
    }

    private static long resolveSeed(Cluster cluster, CellGrid cellGrid) {
        AtmosCell cell = cellGrid.getCell(cluster.anchorCoord());
        return (cell != null) ? cell.deterministicSeed() : NEUTRAL_SEED;
    }
}