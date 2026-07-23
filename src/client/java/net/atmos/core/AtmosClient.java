package net.atmos.core;

import net.atmos.aps.OptimizationPlan;
import net.atmos.aps.OptimizationPlanManager;
import net.atmos.aps.PerformanceSnapshot;
import net.atmos.aps.PerformanceSnapshotBridge;
import net.atmos.aps.TelemetryCollector;
import net.atmos.aps.TelemetryManager;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogManager;
import net.atmos.atmosphere.sky.MoonlightController;
import net.atmos.atmosphere.sky.SkyColorController;
import net.atmos.atmosphere.sky.SunGlareController;
import net.atmos.cellgrid.CellGrid;
import net.atmos.cluster.Cluster;
import net.atmos.cluster.ClusterBuilder;
import net.atmos.compat.ShaderDetector;
import net.atmos.composition.Composition;
import net.atmos.composition.CompositionEngine;
import net.atmos.composition.CompositionInputs;
import net.atmos.config.AtmosConfig;
import net.atmos.director.AtmosphereDirector;
import net.atmos.director.DirectorInputs;
import net.atmos.director.DirectorState;
import net.atmos.exposure.ExposureInputs;
import net.atmos.exposure.ExposureModel;
import net.atmos.exposure.ExposureStateManager;
import net.atmos.exposure.ExposureStateSnapshot;
import net.atmos.lighting.AtmosphericLightingPipeline;
import net.atmos.lighting.LightingSnapshot;
import net.atmos.memory.CellMemoryIntegrator;
import net.atmos.render.ALSSRenderer;
import net.atmos.render.ClusterGeometry;
import net.atmos.render.GeometryGenerator;
import net.atmos.render.RenderCluster;
import net.atmos.render.RenderClusterConstructionStage;
import net.atmos.render.RendererExpansion;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AtmosClient implements ClientModInitializer {

	private static final FogManager               FOG_MANAGER           = new FogManager();
	private static final SkyColorController        SKY_COLOR_CONTROLLER  = new SkyColorController();
	private static final SunGlareController        SUN_GLARE_CONTROLLER  = new SunGlareController();
	private static final MoonlightController       MOONLIGHT_CONTROLLER  = new MoonlightController();

	// Cell Grid (Chapter 6 / Appendix F §3). Owns spatial cell lifecycle and
	// Horizon Map / Canopy Profile generation only.
	//
	// Lifecycle split (audit Finding F fix): dimension change calls
	// CELL_GRID.reset() (flush only). DISCONNECT calls CELL_GRID.shutdown()
	// (flush, then bounded drain-and-stop of the persistence layer's
	// background thread) — see both handlers below.
	private static final CellGrid CELL_GRID = new CellGrid();

	// Chapter 13 Stage 2/4 — per-cell Historical Memory advancement.
	private static final CellMemoryIntegrator CELL_MEMORY_INTEGRATOR = new CellMemoryIntegrator();

	// Chapter 16 Stage 2 (Appendix D §2) — Render Thread telemetry collector.
	private static final TelemetryCollector TELEMETRY_COLLECTOR = new TelemetryCollector();

	// Chapter 9 Stage 5 (Appendix Y §Runtime Ownership: "AtmosClient owns...
	// Frame coordination"; Stage 5 requirement 2: "Make AtmosClient the sole
	// runtime integration owner"). Owned directly here, matching the
	// FOG_MANAGER/CELL_GRID precedent — no separate coordinator class exists.
	private static final AtmosphereDirector DIRECTOR       = new AtmosphereDirector();
	private static final ExposureModel      EXPOSURE_MODEL = new ExposureModel();

	// skyContext is written once per frame at WorldRenderEvents.START and read
	// by SkyMixin. Nulled on disconnect and dimension change so SkyMixin's
	// null check catches the gap before the next valid frame.
	private static FogContext skyContext = null;

	// Delta time for sky color smoothing.
	private static float skyDeltaSec  = 0f;
	private static long  skyLastNanos = -1L;

	// Dimension tracking for mid-session change detection.
	private static ResourceKey<Level> currentDimension = null;

	@Override
	public void onInitializeClient() {
		AtmosConfig.load();
		ShaderDetector.init();

		WorldRenderEvents.START.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.cameraEntity == null) return;

			TELEMETRY_COLLECTOR.beginFrame();

			// Appendix F §1 — single Render Thread writer call site.
			// NOTE: this snapshot must remain valid for the rest of the
			// frame, including the dimension-change branch below.
			CameraManager.publish(context);

			SKY_COLOR_CONTROLLER.beginFrame();

			// --- Dimension change detection ---
			// Portal travel changes mc.level without firing DISCONNECT.
			ResourceKey<Level> newDimension = mc.level.dimension();
			if (currentDimension != null && !currentDimension.equals(newDimension)) {
				FOG_MANAGER.reset();
				SKY_COLOR_CONTROLLER.reset();
				CELL_GRID.reset();
				CELL_MEMORY_INTEGRATOR.reset();
				TELEMETRY_COLLECTOR.reset();
				DIRECTOR.reset();
				EXPOSURE_MODEL.reset();
				ExposureStateManager.reset();
				skyContext   = null;
				skyLastNanos = -1L;
				skyDeltaSec  = 0f;
			}
			currentDimension = newDimension;

			skyContext = FogContext.capture(mc.gameRenderer.getMainCamera(), mc.level);
			FOG_MANAGER.update(mc.gameRenderer.getMainCamera(), mc.level);

			CELL_GRID.update(mc.level, mc.gameRenderer.getMainCamera().getBlockPosition());

			long now     = System.nanoTime();
			skyDeltaSec  = (skyLastNanos < 0) ? 0f
					: Math.min((now - skyLastNanos) / 1_000_000_000f, 0.1f);
			skyLastNanos = now;

			CELL_MEMORY_INTEGRATOR.update(CELL_GRID, FOG_MANAGER.getEnvState(), skyDeltaSec);

			// Chapter 9 Stage 5 (Appendix Y) — canonical runtime pipeline,
			// invoked directly by AtmosClient inside its single registered
			// callback, per the Single Entry Rule.
			runRenderPipeline(mc.level, skyContext, skyDeltaSec);

			TELEMETRY_COLLECTOR.endFrame(CELL_GRID.getActiveCells().size());
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FOG_MANAGER.reset();
			SKY_COLOR_CONTROLLER.reset();
			CameraManager.reset();
			CELL_GRID.shutdown();
			CELL_MEMORY_INTEGRATOR.reset();
			TELEMETRY_COLLECTOR.reset();
			TelemetryManager.reset();
			DIRECTOR.reset();
			EXPOSURE_MODEL.reset();
			ExposureStateManager.reset();
			skyContext        = null;
			skyLastNanos      = -1L;
			skyDeltaSec       = 0f;
			currentDimension  = null;
		});
	}

	/**
	 * Chapter 9 Stage 5 pipeline body (Appendix Y): Cell Grid clusters ->
	 * Composition -> Director -> Lighting -> Exposure -> Performance
	 * Snapshot -> RenderCluster Construction -> Renderer Expansion ->
	 * Geometry Generation -> ALSSRenderer. Owned by AtmosClient directly —
	 * private helper, not a delegate object (Appendix Y "sole runtime
	 * integration owner"). Per Appendix Y §10 / ZC §6, a candidate missing
	 * a required producer is silently excluded, never fabricated.
	 */
	private static void runRenderPipeline(ClientLevel level, FogContext skyContext, float deltaSec) {
		CameraSnapshot cameraSnapshot = CameraManager.get();
		if (cameraSnapshot == null) return;

		EnvironmentalState env = FOG_MANAGER.getEnvState();
		Holder<Biome> biome = skyContext.biome();
		float sunAngleRadians = skyContext.sunAngle();

		List<Cluster> candidates = ClusterBuilder.build(CELL_GRID, env);
		Composition composition = CompositionEngine.compose(
				new CompositionInputs(candidates, cameraSnapshot, env));

		OptimizationPlan optimizationPlan = OptimizationPlanManager.get();
		LocalPlayer player = Minecraft.getInstance().player;
		Vec3 playerPosition = (player != null) ? player.position() : null;

		DirectorState directorState = DIRECTOR.update(new DirectorInputs(
				env, composition, biome, sunAngleRadians, optimizationPlan,
				level.getRainLevel(1.0f), level.getThunderLevel(1.0f), playerPosition
		), deltaSec);

		// Consumed downstream by RenderCluster Construction's Color,
		// Definition, and Distance producers (Appendix ZB Blockers 2-4).
		LightingSnapshot lighting = AtmosphericLightingPipeline.evaluate(
				cameraSnapshot, env, directorState, sunAngleRadians);

		EXPOSURE_MODEL.update(new ExposureInputs(
				env, CELL_GRID, null, composition, directorState, optimizationPlan, sunAngleRadians
		), deltaSec);
		ExposureStateSnapshot exposureSnapshot = ExposureStateManager.get();
		float exposureScale = (exposureSnapshot != null) ? exposureSnapshot.exposureScale() : 1.0f;

		PerformanceSnapshot performanceSnapshot = PerformanceSnapshotBridge.current();

		// Appendix ZB §I — continuous simulation time in seconds, derived
		// from world time ticks plus the current partial tick.
		float gameTimeSeconds = (level.getGameTime() + cameraSnapshot.partialTick()) / 20.0f;

		// Chapter 8 Stage Five input — smoothed values already sampled once
		// per frame into skyContext (FogContext.rain()/thunder()), per
		// WeatherAttenuationEvaluator's documented contract.
		float rainLevel = skyContext.rain();
		float thunderLevel = skyContext.thunder();

		List<RenderCluster> renderClusters = buildRenderClusters(
				composition, cameraSnapshot, lighting, env, directorState, performanceSnapshot,
				sunAngleRadians, exposureScale, skyContext.renderDistance(), gameTimeSeconds,
				rainLevel, thunderLevel);

		List<ClusterGeometry> geometries = new ArrayList<>(renderClusters.size());
		for (RenderCluster cluster : renderClusters) {
			geometries.add(GeometryGenerator.generate(cluster, RendererExpansion.expand(cluster)));
		}

		MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
		ALSSRenderer.render(bufferSource, cameraSnapshot, geometries);
	}

	/**
	 * Attempts RenderCluster Construction for every classified cluster via
	 * {@link RenderClusterConstructionStage#construct}, which orchestrates
	 * every approved Appendix ZB Blocker producer, per-cluster Confidence,
	 * and the full SunReach pipeline for that cluster.
	 */
	private static List<RenderCluster> buildRenderClusters(
			Composition composition,
			CameraSnapshot camera,
			LightingSnapshot lighting,
			EnvironmentalState env,
			DirectorState directorState,
			PerformanceSnapshot performanceSnapshot,
			float sunAngleRadians,
			float exposureScale,
			int renderDistanceChunks,
			float gameTimeSeconds,
			float rainLevel,
			float thunderLevel
	) {
		List<Optional<RenderCluster>> attempts = new ArrayList<>();

		if (composition.heroCluster() != null) {
			attempts.add(RenderClusterConstructionStage.construct(
					composition.heroCluster(), RenderCluster.Role.HERO,
					camera, lighting, env, directorState, performanceSnapshot, CELL_GRID,
					sunAngleRadians, exposureScale, renderDistanceChunks, gameTimeSeconds,
					rainLevel, thunderLevel));
		}
		for (Cluster c : composition.secondaryClusters()) {
			attempts.add(RenderClusterConstructionStage.construct(
					c, RenderCluster.Role.SECONDARY,
					camera, lighting, env, directorState, performanceSnapshot, CELL_GRID,
					sunAngleRadians, exposureScale, renderDistanceChunks, gameTimeSeconds,
					rainLevel, thunderLevel));
		}
		for (Cluster c : composition.ambientClusters()) {
			attempts.add(RenderClusterConstructionStage.construct(
					c, RenderCluster.Role.AMBIENT,
					camera, lighting, env, directorState, performanceSnapshot, CELL_GRID,
					sunAngleRadians, exposureScale, renderDistanceChunks, gameTimeSeconds,
					rainLevel, thunderLevel));
		}

		return RenderClusterConstructionStage.publishAll(attempts);
	}

	public static FogManager               getFogManager()               { return FOG_MANAGER;               }
	public static SkyColorController       getSkyColorController()       { return SKY_COLOR_CONTROLLER;      }
	public static SunGlareController       getSunGlareController()       { return SUN_GLARE_CONTROLLER;      }
	public static MoonlightController      getMoonlightController()      { return MOONLIGHT_CONTROLLER;      }
	public static CellGrid                 getCellGrid()                 { return CELL_GRID;                 }

	public static FogContext getSkyContext()   { return skyContext;  }
	public static float      getSkyDeltaSec() { return skyDeltaSec; }
}