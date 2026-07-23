package net.atmos.render;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;
import net.atmos.aps.PerformanceSnapshot;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;
import net.atmos.cluster.Cluster;
import net.atmos.composition.ClusterConfidenceEvaluator;
import net.atmos.core.CameraSnapshot;
import net.atmos.director.DirectorState;
import net.atmos.lighting.LightingSnapshot;
import net.atmos.sunreach.BiomeModifierEvaluator;
import net.atmos.sunreach.BiomeModifierResult;
import net.atmos.sunreach.CanopyOcclusionEvaluator;
import net.atmos.sunreach.CanopyOcclusionResult;
import net.atmos.sunreach.SkyVisibilityEvaluator;
import net.atmos.sunreach.SkyVisibilityResult;
import net.atmos.sunreach.SunReachCombinationEvaluator;
import net.atmos.sunreach.SunReachCombinationResult;
import net.atmos.sunreach.SunReachEvaluator;
import net.atmos.sunreach.SunReachResult;
import net.atmos.sunreach.WeatherAttenuationEvaluator;
import net.atmos.sunreach.WeatherAttenuationResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RenderCluster Construction — Chapter 9 Stage 5 Phase 5 (Appendix L, Appendix P).
 *
 * {@link #construct} orchestrates every approved Appendix ZB Blocker producer
 * (Solar Direction, Width/Length, Color, Definition, Distance, Animation, LOD,
 * Final Alpha) plus per-cluster Confidence (Chapter 4) and the full SunReach
 * pipeline (Chapter 8 Stages 1-2, 3, 4, 5, 7, combined per Appendix K), then
 * delegates assembly to {@link #attemptConstruct}. It owns no rendering or
 * atmospheric mathematics itself — every field originates from exactly one
 * producer, per the Single Producer Rule (Appendix P).
 *
 * --- SunReach resolution (Appendix ZD §5) ---
 *
 * The cluster's anchor cell is looked up via
 * {@code cellGrid.getCell(cluster.anchorCoord())} — the same pattern already
 * used by {@link AnimationPhaseEvaluator}. Its HorizonMap and CanopyProfile
 * feed SunReachEvaluator (Stages 1-2), SkyVisibilityEvaluator (Stage 3), and
 * CanopyOcclusionEvaluator (Stage 4); its biome feeds BiomeModifierEvaluator
 * (Stage 7); the caller-supplied smoothed rain/thunder feed
 * WeatherAttenuationEvaluator (Stage 5). SunReachCombinationEvaluator
 * combines all five per Appendix K's canonical formula and order. Stage Six
 * (Humidity Interaction) is intentionally excluded per Appendix K §K.4.
 *
 * If the anchor cell is unavailable, SunReach remains empty and construction
 * fails for that cluster only (Appendix L §8 — no fabricated SunReach, no
 * partial RenderClusters).
 */
public final class RenderClusterConstructionStage {

    private RenderClusterConstructionStage() {}

    // Chapter 10 Part 2 Brightness Hierarchy, reused verbatim (identical values
    // to DensityProbabilityMap's private per-role weighting; duplicated here
    // rather than exposed, since Renderer Expansion internals are out of scope).
    private static final float HERO_COMPOSITION_WEIGHT = 1.00f;
    private static final float SECONDARY_COMPOSITION_WEIGHT = 0.70f;
    private static final float AMBIENT_COMPOSITION_WEIGHT = 0.40f;

    /**
     * Orchestrates every approved Blocker producer for one candidate {@link Cluster}.
     * Per Appendix ZB Blocker 8, Solar Direction and Width/Length mapping only run
     * while direct sunlight is present ({@code lighting.lightIntensity() > 0}).
     *
     * @param rainLevel    smoothed rain intensity, sourced from FogContext.rain()
     *                     (Appendix ZC/ZD, matching WeatherAttenuationEvaluator's
     *                     documented input contract).
     * @param thunderLevel smoothed thunder intensity, sourced from FogContext.thunder().
     */
    public static Optional<RenderCluster> construct(
            Cluster cluster,
            RenderCluster.Role role,
            CameraSnapshot camera,
            LightingSnapshot lighting,
            EnvironmentalState env,
            DirectorState directorState,
            PerformanceSnapshot performanceSnapshot,
            CellGrid cellGrid,
            float sunAngleRadians,
            float exposureScale,
            int renderDistanceChunks,
            float gameTimeSeconds,
            float rainLevel,
            float thunderLevel
    ) {
        if (lighting.lightIntensity() <= 0f) {
            return Optional.empty();
        }

        SolarDirectionResult solar = SolarDirectionProvider.evaluate(sunAngleRadians);
        WidthLengthResult widthLength = WidthLengthMappingEvaluator.evaluate(cluster.radius(), sunAngleRadians);
        ColorResult color = ColorProducer.evaluate(lighting);
        DefinitionResult definition = DefinitionProducer.evaluate(env);

        float cameraDistance = (float) camera.position().distanceTo(cluster.centerWorldPos());
        DistanceEvaluationResult distance = DistanceEvaluationEvaluator.evaluate(
                cameraDistance, directorState.fogDensity(), renderDistanceChunks);

        AnimationPhaseResult animation = AnimationPhaseEvaluator.evaluate(
                gameTimeSeconds, env, cluster, cellGrid);

        LodAssignmentResult lod = LodAssignmentEvaluator.evaluate(cameraDistance, performanceSnapshot);

        float confidence = ClusterConfidenceEvaluator.evaluate(cluster, camera).value();
        float compositionWeight = compositionWeightFor(role);

        Optional<Float> sunReach = resolveSunReach(cluster, cellGrid, sunAngleRadians, rainLevel, thunderLevel);

        Optional<Float> alpha = sunReach.map(reach -> FinalAlphaAssemblyEvaluator.evaluate(
                confidence, reach, exposureScale, distance.fadeWeight(), compositionWeight, lod.lodWeight()
        ).finalAlpha());

        return attemptConstruct(
                cluster.centerWorldPos(),
                Optional.of(solar.direction()),
                Optional.of(widthLength.width()),
                Optional.of(widthLength.length()),
                alpha,
                Optional.of(color.color()),
                Optional.of(definition.definitionScale()),
                exposureScale,
                Optional.of(distance.maxRenderDistance()),
                role,
                Optional.of(animation.animationPhase()),
                Optional.of(lod.lodLevel()),
                sunReach
        );
    }

    /**
     * Chapter 8 Stages 1-2/3/4/5/7 -> Appendix K Final SunReach Combination.
     * Resolves the cluster's anchor {@link AtmosCell} via the Cell Grid;
     * returns empty if the cell is unavailable (Appendix L §8 — no
     * fabricated SunReach).
     */
    private static Optional<Float> resolveSunReach(
            Cluster cluster, CellGrid cellGrid, float sunAngleRadians, float rainLevel, float thunderLevel) {

        AtmosCell cell = cellGrid.getCell(cluster.anchorCoord());
        if (cell == null) {
            return Optional.empty();
        }

        SunReachResult sunReachResult = SunReachEvaluator.evaluate(cell.horizonMap(), sunAngleRadians);
        SkyVisibilityResult skyVisibilityResult = SkyVisibilityEvaluator.evaluate(cell.horizonMap());
        CanopyOcclusionResult canopyOcclusionResult = CanopyOcclusionEvaluator.evaluate(cell.canopyProfile());
        WeatherAttenuationResult weatherAttenuationResult =
                WeatherAttenuationEvaluator.evaluate(rainLevel, thunderLevel);
        BiomeModifierResult biomeModifierResult =
                BiomeModifierEvaluator.evaluate(BiomeAtmosphereRegistry.of(cell.biome()).fog());

        SunReachCombinationResult combined = SunReachCombinationEvaluator.evaluate(
                sunReachResult, skyVisibilityResult, canopyOcclusionResult,
                weatherAttenuationResult, biomeModifierResult);

        return Optional.of(combined.finalSunReach());
    }

    private static float compositionWeightFor(RenderCluster.Role role) {
        return switch (role) {
            case HERO -> HERO_COMPOSITION_WEIGHT;
            case SECONDARY -> SECONDARY_COMPOSITION_WEIGHT;
            case AMBIENT -> AMBIENT_COMPOSITION_WEIGHT;
        };
    }

    /** Assembly-only stage per Appendix L §10: collects already-finalized producer outputs and constructs immutable RenderClusters. */
    public static Optional<RenderCluster> attemptConstruct(
            Vec3 position,
            Optional<Vec3> direction,
            Optional<Float> width,
            Optional<Float> length,
            Optional<Float> alpha,
            Optional<RenderColor> color,
            Optional<Float> definition,
            float exposureScale,
            Optional<Float> fadeDistance,
            RenderCluster.Role role,
            Optional<Float> animationPhase,
            Optional<Integer> lodLevel,
            Optional<Float> sunReach
    ) {
        if (direction.isEmpty() || width.isEmpty() || length.isEmpty() || alpha.isEmpty()
                || color.isEmpty() || definition.isEmpty() || fadeDistance.isEmpty()
                || animationPhase.isEmpty() || lodLevel.isEmpty() || sunReach.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RenderCluster(
                position, direction.get(), width.get(), length.get(),
                alpha.get(), color.get(), definition.get(), exposureScale,
                fadeDistance.get(), role, animationPhase.get(), lodLevel.get(), sunReach.get()
        ));
    }

    /** Publishes only successfully constructed clusters — Appendix L §5 (immutable, disposable, per-frame). */
    public static List<RenderCluster> publishAll(List<Optional<RenderCluster>> attempts) {
        List<RenderCluster> result = new ArrayList<>(attempts.size());
        for (Optional<RenderCluster> attempt : attempts) {
            attempt.ifPresent(result::add);
        }
        return List.copyOf(result);
    }
}