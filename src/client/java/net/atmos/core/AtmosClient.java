package net.atmos.core;

import net.atmos.aps.*;
import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogManager;
import net.atmos.atmosphere.sky.*;
import net.atmos.cellgrid.CellGrid;
import net.atmos.cloud.CloudManager;
import net.atmos.cloud.CloudRenderer;
import net.atmos.cluster.Cluster;
import net.atmos.cluster.ClusterBuilder;
import net.atmos.compat.ShaderDetector;
import net.atmos.composition.Composition;
import net.atmos.composition.CompositionEngine;
import net.atmos.composition.CompositionInputs;
import net.atmos.config.AtmosConfig;
import net.atmos.config.AtmosConfigWatcher;
import net.atmos.config.AtmosReloadManager;
import net.atmos.config.AtmosSystemRegistry;
import net.atmos.diagnostics.*;
import net.atmos.director.AtmosphereDirector;
import net.atmos.director.DirectorInputs;
import net.atmos.director.DirectorState;
import net.atmos.exposure.*;
import net.atmos.lighting.AtmosphericLightingPipeline;
import net.atmos.lighting.LightingSnapshot;
import net.atmos.memory.CellMemoryIntegrator;
import net.atmos.overlay.*;
import net.atmos.render.RenderCluster;
import net.atmos.render.RenderClusterConstructionStage;
import net.atmos.render.RenderPipelineCache;
import net.atmos.seasonal.ClimateContext;
import net.atmos.seasonal.SeasonalFeelingSystem;
import net.atmos.command.AtmosCommandRegistry;
import net.atmos.command.SeasonDebugState;
import net.atmos.seasonal.SeasonalFeelingStateManager;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.atmos.ui.AtmosConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Batch 1 (Performance Stabilization) — restructured.
 *
 * BEFORE: every subsystem (Fog/Sky simulation, Cell Grid, Cluster Builder,
 * Composition, Director, SunReach, RenderCluster Construction, Overlay
 * simulation/dirty-processing) ran unconditionally inside
 * WorldRenderEvents.START — i.e. once per RENDERED FRAME. At 600 FPS this
 * meant 600 full pipeline evaluations per second, most of them producing
 * output nothing consumed that frame (ALSSRenderer never wired to draw
 * RenderCluster output) or recomputing state that hadn't changed.
 *
 * AFTER: an AtmosTickScheduler runs at CLIENT TICK rate (20/sec, independent
 * of render FPS), phase-split across 4 ticks so no single tick pays for
 * every subsystem. WorldRenderEvents.START/AFTER_TRANSLUCENT now only:
 *   1. Update the two lightweight per-frame values vanilla's own fog/sky
 *      mixins synchronously depend on (FogManager/EnvironmentalState —
 *      required every frame because Minecraft's own setupFog/getSkyColor
 *      calls happen mid-render-frame and must reflect current state), and
 *   2. Read already-published cached results (RenderPipelineCache,
 *      OverlayManager, OverlayChunkSurfaceCache) to draw.
 *
 * No visual behavior changes — only when and how often work happens.
 */
public class AtmosClient implements ClientModInitializer {

	private static final FogManager FOG_MANAGER = new FogManager();
	private static final SkyColorController SKY_COLOR_CONTROLLER = new SkyColorController();
	private static final SunGlareController SUN_GLARE_CONTROLLER = new SunGlareController();
	private static final MoonlightController MOONLIGHT_CONTROLLER = new MoonlightController();
	private static final SkyPhaseController SKY_PHASE_CONTROLLER = new SkyPhaseController();

	private static final CellGrid CELL_GRID = new CellGrid();
	private static final CellMemoryIntegrator CELL_MEMORY_INTEGRATOR = new CellMemoryIntegrator();
	private static final TelemetryCollector TELEMETRY_COLLECTOR = new TelemetryCollector();

	private static final AtmosphereDirector DIRECTOR = new AtmosphereDirector();
	private static final ExposureModel EXPOSURE_MODEL = new ExposureModel();

	private static final OverlayInvalidationQueue OVERLAY_INVALIDATION_QUEUE = new OverlayInvalidationQueue();
	private static final OverlayLevelCrossingScheduler<SurfaceTransitionKey> OVERLAY_LEVEL_CROSSING_SCHEDULER =
			new OverlayLevelCrossingScheduler<>();

	private static final OverlayChunkSurfaceCache OVERLAY_CHUNK_CACHE =
			new OverlayChunkSurfaceCache(OVERLAY_INVALIDATION_QUEUE, OVERLAY_LEVEL_CROSSING_SCHEDULER);
	private static final OverlayManager OVERLAY_MANAGER = new OverlayManager();
	private static final OverlayRenderer OVERLAY_RENDERER =
			new OverlayRenderer(OVERLAY_MANAGER, OVERLAY_CHUNK_CACHE, OVERLAY_CHUNK_CACHE.getStateStore(), OVERLAY_INVALIDATION_QUEUE);
	private static final OverlayAccumulationSimulation OVERLAY_SIMULATION =
			new OverlayAccumulationSimulation(OVERLAY_CHUNK_CACHE.getStateStore(), OVERLAY_MANAGER);

	private static final CloudManager CLOUD_MANAGER = new CloudManager();
	private static final CloudRenderer CLOUD_RENDERER = new CloudRenderer(CLOUD_MANAGER);

	private static final SeasonalFeelingSystem SEASONAL_FEELING_SYSTEM = new SeasonalFeelingSystem();

	// --- Batch 1: tick scheduler + per-subsystem gates ---
	private static final AtmosTickScheduler TICK_SCHEDULER = new AtmosTickScheduler();
	private static final VersionGate CLUSTER_INPUT_GATE = new VersionGate();
	private static final UpdateInterval DIRECTOR_INTERVAL = new UpdateInterval(20);
	private static final UpdateInterval EXPOSURE_INTERVAL = new UpdateInterval(10);
	private static final UpdateInterval LIGHTING_INTERVAL = new UpdateInterval(20);
	private static final UpdateInterval AIR_DIAG_INTERVAL = new UpdateInterval(20);

	// Last composition/candidate state, reused by the SLOW_SYSTEMS phase and
	// by the render-thread reader when the PIPELINE phase decided not to
	// recompute this tick.
	private static List<Cluster> lastCandidates = List.of();
	private static Composition lastComposition = new Composition(null, List.of(), List.of(), List.of());

	private static FogContext skyContext = null;
	private static float skyDeltaSec = 0f;
	private static long skyLastNanos = -1L;

	private static ResourceKey<Level> currentDimension = null;

	private static KeyMapping diagnosticToggleKey;
	private static KeyMapping configReloadKey;

	@Override
	public void onInitializeClient() {
		AtmosConfig.load();
		AtmosCommandRegistry.register();
		ShaderDetector.init();

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public net.minecraft.resources.ResourceLocation getFabricId() {
						return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("atmos", "overlay_textures");
					}

					@Override
					public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
						OverlayTextureRegistry.clearCache();
					}
				});

		AtmosSystemRegistry.registerAll(FOG_MANAGER, SKY_PHASE_CONTROLLER);
		AtmosReloadManager.register(OVERLAY_RENDERER);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(OVERLAY_RENDERER::render);

		ClientChunkEvents.CHUNK_LOAD.register(OVERLAY_CHUNK_CACHE::onChunkLoad);
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			OVERLAY_CHUNK_CACHE.onChunkUnload(chunk);
			OVERLAY_RENDERER.onChunkUnload(chunk.getPos());
		});

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public net.minecraft.resources.ResourceLocation getFabricId() {
						return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("atmos", "cloud_textures");
					}

					@Override
					public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
						CLOUD_MANAGER.initialize();
					}
				});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(CLOUD_RENDERER::render);

		if (AtmosConfig.get().debug.configAutoReload) {
			AtmosConfigWatcher.start();
		}

		diagnosticToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.atmos.toggle_diagnostics",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F8,
				"category.atmos.keys"
		));
		configReloadKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.atmos.reload_config",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F9,
				"category.atmos.keys"
		));

		KeyMapping configScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.atmos.open_config",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_BRACKET,
				"category.atmos.keys"
		));

		registerSchedulerPhases();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (diagnosticToggleKey.consumeClick()) {
				DiagnosticMode[] modes = DiagnosticMode.values();
				DiagnosticMode next = modes[(DiagnosticManager.MODE.ordinal() + 1) % modes.length];
				DiagnosticManager.MODE = next;

				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("Atmos Diagnostic Mode: " + next.name()), true
					);
				}
			}

			while (configReloadKey.consumeClick()) {
				AtmosReloadManager.reloadAll();
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("Atmos: Configuration Reloaded"), true
					);
				}
			}

			while (configScreenKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(AtmosConfigScreen.create(null));
				}
			}

			if (client.level != null && client.player != null) {
				TICK_SCHEDULER.tick();
			}
		});

		WorldRenderEvents.START.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.cameraEntity == null) return;

			TELEMETRY_COLLECTOR.beginFrame();
			CameraManager.publish(context);
			SKY_COLOR_CONTROLLER.beginFrame();
			SKY_PHASE_CONTROLLER.beginFrame();

			DiagnosticManager.beginFrame();

			ResourceKey<Level> newDimension = mc.level.dimension();
			if (currentDimension != null && !currentDimension.equals(newDimension)) {
				handleDimensionChange();
			}
			currentDimension = newDimension;

			// --- Per-frame only: values vanilla's fog/sky mixins consume
			// synchronously mid-render-frame (FogRenderer#setupFog,
			// ClientLevel#getSkyColor). These are cheap drifter advances,
			// not the heavy Cluster/Composition/SunReach pipeline, and
			// must remain frame-synchronous because vanilla calls them
			// from within this same frame via mixins.
			DiagnosticHooks.beginStage(PipelineStage.ENVIRONMENTAL_STATE);
			try {
				skyContext = FogContext.capture(mc.gameRenderer.getMainCamera(), mc.level);
				FOG_MANAGER.update(mc.gameRenderer.getMainCamera(), mc.level);

				EnvironmentalState envForDiag = FOG_MANAGER.getEnvState();
				if (AIR_DIAG_INTERVAL.shouldRun(TICK_SCHEDULER.currentTick())) {
					DiagnosticHooks.recordFullAirState(
							AtmosConfig.get().air.airSimulationEnabled,
							envForDiag.getAirPressure(), envForDiag.getAirDensity(),
							envForDiag.getAtmosphericStability(), envForDiag.getTurbulence(),
							envForDiag.getAerosolDensity()
					);
				}
			} finally {
				DiagnosticHooks.endStage(PipelineStage.ENVIRONMENTAL_STATE);
			}

			DiagnosticValidator.validateFogState(
					FOG_MANAGER.getFogStart(), FOG_MANAGER.getFogEnd(),
					FOG_MANAGER.getFogRed(), FOG_MANAGER.getFogGreen(), FOG_MANAGER.getFogBlue(), 1.0f
			);

			long now = System.nanoTime();
			skyDeltaSec = (skyLastNanos < 0) ? 0f : Math.min((now - skyLastNanos) / 1_000_000_000f, 0.1f);
			skyLastNanos = now;

			TELEMETRY_COLLECTOR.endFrame(CELL_GRID.getActiveCells().size());

			CameraSnapshot cameraSnapshot = CameraManager.get();
			Holder<Biome> biome = mc.level.getBiome(mc.gameRenderer.getMainCamera().getBlockPosition());
			DiagnosticManager.endFrame(
					cameraSnapshot.position().x, cameraSnapshot.position().y, cameraSnapshot.position().z,
					mc.level.getRainLevel(1.0f), mc.level.getThunderLevel(1.0f),
					biome, mc.level.dimension().location().toString()
			);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (DiagnosticManager.isActive()) {
				DiagnosticContext ctx = new DiagnosticContext(DiagnosticManager.MODE, DiagnosticClock.currentTimeMillis(), "Atmos-P3");
				String report = BasicReportGenerator.generate(DiagnosticManager.getHistory(), ctx);
				if (DiagnosticManager.MODE == DiagnosticMode.FULL) {
					report += "\n\n" + FullReportGenerator.generateFull(DiagnosticManager.getFullContext());
				}
				DiagnosticFileWriter.writeReport(report);
			}

			fullReset();
		});
	}

	// -------------------------------------------------------------------
	// Batch 1 Phase 0/1/3: tick-scheduler phase registration
	// -------------------------------------------------------------------

	private void registerSchedulerPhases() {
		TICK_SCHEDULER.register(AtmosTickScheduler.Phase.PIPELINE, AtmosClient::runPipelinePhase);
		TICK_SCHEDULER.register(AtmosTickScheduler.Phase.OVERLAY, AtmosClient::runOverlayPhase);
		TICK_SCHEDULER.register(AtmosTickScheduler.Phase.ENVIRONMENT, AtmosClient::runEnvironmentPhase);
		TICK_SCHEDULER.register(AtmosTickScheduler.Phase.SLOW_SYSTEMS, AtmosClient::runSlowSystemsPhase);
	}

	/** Cluster Builder -> Composition -> Director -> SunReach -> RenderCluster Construction. */
	private static void runPipelinePhase() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || skyContext == null) return;
		CameraSnapshot cameraSnapshot = CameraManager.get();
		if (cameraSnapshot == null) return;

		EnvironmentalState env = FOG_MANAGER.getEnvState();

		DiagnosticHooks.beginStage(PipelineStage.CELL_GRID);
		try {
			CELL_GRID.update(mc.level, mc.gameRenderer.getMainCamera().getBlockPosition());
		} finally {
			DiagnosticHooks.endStage(PipelineStage.CELL_GRID);
		}

		CELL_MEMORY_INTEGRATOR.update(CELL_GRID, env, 1.0f / 20.0f * 4);

		// Batch 1 Phase 3: only recluster when Cell Grid's active set
		// actually changed structurally since the last pipeline run.
		DiagnosticHooks.beginStage(PipelineStage.CLUSTER_BUILDER);
		try {
			if (CLUSTER_INPUT_GATE.changed(CELL_GRID.structuralVersion())) {
				lastCandidates = ClusterBuilder.build(CELL_GRID, env);
				for (Cluster c : lastCandidates) {
					DiagnosticHooks.recordFullCandidate(
							c.anchorCoord().toString(),
							c.centerWorldPos().x, c.centerWorldPos().y, c.centerWorldPos().z,
							c.radius(), c.radius(), c.cellCount(), 0L, "Flood Fill"
					);
				}
				DiagnosticHooks.recordEventCount(DiagnosticEvent.CLUSTER_GENERATED, lastCandidates.size());

				DiagnosticHooks.beginStage(PipelineStage.COMPOSITION);
				try {
					lastComposition = CompositionEngine.compose(
							new CompositionInputs(lastCandidates, cameraSnapshot, env));
				} finally {
					DiagnosticHooks.endStage(PipelineStage.COMPOSITION);
				}

				int acceptedComps = (lastComposition.heroCluster() != null ? 1 : 0)
						+ lastComposition.secondaryClusters().size() + lastComposition.ambientClusters().size();
				DiagnosticHooks.recordEventCount(DiagnosticEvent.COMPOSITION_ACCEPTED, acceptedComps);
				DiagnosticHooks.recordEventCount(DiagnosticEvent.COMPOSITION_REJECTED, lastComposition.rejectedClusters().size());
			}
		} finally {
			DiagnosticHooks.endStage(PipelineStage.CLUSTER_BUILDER);
		}

		Holder<Biome> biome = skyContext.biome();
		float sunAngleRadians = skyContext.sunAngle();

		ExposureStateSnapshot exposureSnapshot = ExposureStateManager.get();
		float exposureScale = (exposureSnapshot != null) ? exposureSnapshot.exposureScale() : 1.0f;

		DiagnosticHooks.recordFullEnvState(
				mc.level.getGameTime(), mc.level.getGameTime(), cameraSnapshot.partialTick(),
				mc.level.getDayTime(), mc.level.getTimeOfDay(cameraSnapshot.partialTick()),
				skyContext.rain(), skyContext.thunder(), skyContext.sunAngle(),
				FOG_MANAGER.getFogEnd(), FOG_MANAGER.getFogRed(), FOG_MANAGER.getFogGreen(), FOG_MANAGER.getFogBlue(), exposureScale,
				skyContext.biome().unwrapKey().map(k -> k.location().toString()).orElse("Unknown"),
				cameraSnapshot.position().x, cameraSnapshot.position().y, cameraSnapshot.position().z,
				cameraSnapshot.lookDirection().toString()
		);

		long tick = TICK_SCHEDULER.currentTick();

		DirectorState directorState;
		if (DIRECTOR_INTERVAL.shouldRun(tick) || lastDirectorState == null) {
			OptimizationPlan optimizationPlan = OptimizationPlanManager.get();
			LocalPlayer player = mc.player;
			Vec3 playerPosition = player.position();

			DiagnosticHooks.beginStage(PipelineStage.DIRECTOR);
			try {
				directorState = DIRECTOR.update(new DirectorInputs(
						env, lastComposition, biome, sunAngleRadians, optimizationPlan,
						mc.level.getRainLevel(1.0f), mc.level.getThunderLevel(1.0f), playerPosition
				), 1.0f);
			} finally {
				DiagnosticHooks.endStage(PipelineStage.DIRECTOR);
			}
			lastDirectorState = directorState;
		} else {
			directorState = lastDirectorState;
		}
		if (directorState == null) return;

		LightingSnapshot lighting;
		if (LIGHTING_INTERVAL.shouldRun(tick) || lastLighting == null) {
			DiagnosticHooks.beginStage(PipelineStage.SUN_REACH);
			try {
				lighting = AtmosphericLightingPipeline.evaluate(cameraSnapshot, env, directorState, sunAngleRadians);
			} finally {
				DiagnosticHooks.endStage(PipelineStage.SUN_REACH);
			}
			lastLighting = lighting;
		} else {
			lighting = lastLighting;
		}

		if (EXPOSURE_INTERVAL.shouldRun(tick)) {
			DiagnosticHooks.beginStage(PipelineStage.EXPOSURE);
			try {
				EXPOSURE_MODEL.update(new ExposureInputs(env, CELL_GRID, null, lastComposition, directorState,
						OptimizationPlanManager.get(), sunAngleRadians), 10.0f / 20.0f);
			} finally {
				DiagnosticHooks.endStage(PipelineStage.EXPOSURE);
			}
		}

		PerformanceSnapshot performanceSnapshot = PerformanceSnapshotBridge.current();

		float gameTimeSeconds = (mc.level.getGameTime() + cameraSnapshot.partialTick()) / 20.0f;
		float rainLevel = skyContext.rain();
		float thunderLevel = skyContext.thunder();

		DiagnosticHooks.beginStage(PipelineStage.RENDER_CLUSTER_CONSTRUCTION);
		List<RenderCluster> renderClusters;
		try {
			renderClusters = buildRenderClusters(
					lastComposition, cameraSnapshot, lighting, env, directorState, performanceSnapshot,
					sunAngleRadians, exposureScale, skyContext.renderDistance(), gameTimeSeconds,
					rainLevel, thunderLevel);
		} finally {
			DiagnosticHooks.endStage(PipelineStage.RENDER_CLUSTER_CONSTRUCTION);
		}

		DiagnosticHooks.recordEventCount(DiagnosticEvent.RENDER_CLUSTER_ACCEPTED, renderClusters.size());

		RenderPipelineCache.publish(new RenderPipelineCache.Snapshot(
				lastCandidates, lastComposition, directorState, renderClusters));
	}

	private static DirectorState lastDirectorState = null;
	private static LightingSnapshot lastLighting = null;

	/** Overlay simulation + budgeted dirty processing. Tick-driven, not frame-driven. */
	private static void runOverlayPhase() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || skyContext == null) return;

		if (!SEASONAL_FEELING_SYSTEM.climateContext().dimensionKey()
				.equals(mc.level.dimension().location().toString())) {
			long pseudoSeed = mc.level.dimension().location().toString().hashCode();
			SEASONAL_FEELING_SYSTEM.initialize(
					ClimateContext.forDimension(mc.level.dimension().location().toString()), pseudoSeed);
		}
		SEASONAL_FEELING_SYSTEM.update(SeasonDebugState.resolveWorldTime(mc.level.getGameTime()));
		DiagnosticHooks.recordFullSeasonalState(SeasonalFeelingStateManager.get());

		OverlaySeasonalPublisher.publish(OVERLAY_MANAGER, SeasonalFeelingStateManager.get());
		OverlayRainPublisher.publish(
				OVERLAY_MANAGER,
				skyContext.rain(),
				FOG_MANAGER.getEnvState().getHumidityMass(),
				FOG_MANAGER.getEnvState().getThermalEnergy());

		float tickDeltaSec = 4.0f / 20.0f;

		net.minecraft.world.level.ChunkPos playerChunk =
				new net.minecraft.world.level.ChunkPos(mc.gameRenderer.getMainCamera().getBlockPosition());
		OVERLAY_CHUNK_CACHE.updateViewerChunk(playerChunk);

		OVERLAY_CHUNK_CACHE.processDirty(mc.level, AtmosConfig.get().overlay.safeDirtyUpdateBudget(), TICK_SCHEDULER.currentTick());
		OVERLAY_MANAGER.update(tickDeltaSec);

		EnvironmentalState env = FOG_MANAGER.getEnvState();
		OverlayEnvironmentalContext overlayCtx = new OverlayEnvironmentalContext(
				env.getNightDepth(),
				env.getThermalEnergy(),
				env.getHumidityMass(),
				skyContext.rain(),
				SeasonalFeelingStateManager.get());

		OVERLAY_CHUNK_CACHE.simulate(OVERLAY_SIMULATION, overlayCtx, playerChunk,
				AtmosConfig.get().overlay.safeSimulationRadiusChunks(), TICK_SCHEDULER.currentTick());
	}

	/** Placeholder for future environment-phase work; currently a no-op since
	 *  Fog/Sky simulation must remain frame-synchronous (see WorldRenderEvents.START). */
	private static void runEnvironmentPhase() {
	}

	private static void runSlowSystemsPhase() {
	}

	private static void handleDimensionChange() {
		FOG_MANAGER.reset();
		SKY_COLOR_CONTROLLER.reset();
		SKY_PHASE_CONTROLLER.reset();
		CELL_GRID.reset();
		CELL_MEMORY_INTEGRATOR.reset();
		TELEMETRY_COLLECTOR.reset();
		DIRECTOR.reset();
		EXPOSURE_MODEL.reset();
		ExposureStateManager.reset();
		SEASONAL_FEELING_SYSTEM.reset();
		OVERLAY_MANAGER.reset();
		OVERLAY_CHUNK_CACHE.reset();
		SeasonDebugState.reset();
		RenderPipelineCache.reset();
		CLUSTER_INPUT_GATE.reset();
		DIRECTOR_INTERVAL.reset();
		EXPOSURE_INTERVAL.reset();
		LIGHTING_INTERVAL.reset();
		AIR_DIAG_INTERVAL.reset();
		OVERLAY_RENDERER.reset();
		CLOUD_RENDERER.reset();
		TICK_SCHEDULER.reset();
		OVERLAY_INVALIDATION_QUEUE.reset();
		OVERLAY_LEVEL_CROSSING_SCHEDULER.reset();
		lastCandidates = List.of();
		lastComposition = new Composition(null, List.of(), List.of(), List.of());
		lastDirectorState = null;
		lastLighting = null;
		skyContext = null;
		skyLastNanos = -1L;
		skyDeltaSec = 0f;
	}

	private static void fullReset() {
		FOG_MANAGER.reset();
		SKY_COLOR_CONTROLLER.reset();
		SKY_PHASE_CONTROLLER.reset();
		CameraManager.reset();
		CELL_GRID.shutdown();
		CELL_MEMORY_INTEGRATOR.reset();
		TELEMETRY_COLLECTOR.reset();
		TelemetryManager.reset();
		DIRECTOR.reset();
		EXPOSURE_MODEL.reset();
		ExposureStateManager.reset();
		OVERLAY_MANAGER.reset();
		OVERLAY_CHUNK_CACHE.reset();
		SeasonDebugState.reset();
		RenderPipelineCache.reset();
		CLUSTER_INPUT_GATE.reset();
		DIRECTOR_INTERVAL.reset();
		EXPOSURE_INTERVAL.reset();
		LIGHTING_INTERVAL.reset();
		AIR_DIAG_INTERVAL.reset();
		OVERLAY_RENDERER.reset();
		CLOUD_RENDERER.reset();
		TICK_SCHEDULER.reset();
		OVERLAY_INVALIDATION_QUEUE.reset();
		OVERLAY_LEVEL_CROSSING_SCHEDULER.reset();
		lastCandidates = List.of();
		lastComposition = new Composition(null, List.of(), List.of(), List.of());
		lastDirectorState = null;
		lastLighting = null;
		skyContext = null;
		skyLastNanos = -1L;
		skyDeltaSec = 0f;
		currentDimension = null;
	}

	private static List<RenderCluster> buildRenderClusters(
			Composition composition, CameraSnapshot camera, LightingSnapshot lighting,
			EnvironmentalState env, DirectorState directorState, PerformanceSnapshot performanceSnapshot,
			float sunAngleRadians, float exposureScale, int renderDistanceChunks, float gameTimeSeconds,
			float rainLevel, float thunderLevel) {
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

	@SuppressWarnings("unused")
	public static FogManager getFogManager() { return FOG_MANAGER; }

	@SuppressWarnings("unused")
	public static OverlayChunkSurfaceCache getOverlayChunkSurfaceCache() { return OVERLAY_CHUNK_CACHE; }

	@SuppressWarnings("unused")
	public static OverlayManager getOverlayManager() { return OVERLAY_MANAGER; }

	@SuppressWarnings("unused")
	public static OverlayRenderer getOverlayRenderer() { return OVERLAY_RENDERER; }

	@SuppressWarnings("unused")
	public static OverlayInvalidationQueue getOverlayInvalidationQueue() { return OVERLAY_INVALIDATION_QUEUE; }

	@SuppressWarnings("unused")
	public static OverlayLevelCrossingScheduler<SurfaceTransitionKey> getOverlayLevelCrossingScheduler() { return OVERLAY_LEVEL_CROSSING_SCHEDULER; }

	@SuppressWarnings("unused")
	public static CloudManager getCloudManager() { return CLOUD_MANAGER; }

	@SuppressWarnings("unused")
	public static CloudRenderer getCloudRenderer() { return CLOUD_RENDERER; }

	@SuppressWarnings("unused")
	public static SkyColorController getSkyColorController() { return SKY_COLOR_CONTROLLER; }

	@SuppressWarnings("unused")
	public static SunGlareController getSunGlareController() { return SUN_GLARE_CONTROLLER; }

	@SuppressWarnings("unused")
	public static MoonlightController getMoonlightController() { return MOONLIGHT_CONTROLLER; }

	@SuppressWarnings("unused")
	public static CellGrid getCellGrid() { return CELL_GRID; }

	@SuppressWarnings("unused")
	public static FogContext getSkyContext() { return skyContext; }

	@SuppressWarnings("unused")
	public static float getSkyDeltaSec() { return skyDeltaSec; }

	@SuppressWarnings("unused")
	public static AtmosTickScheduler getTickScheduler() { return TICK_SCHEDULER; }
}