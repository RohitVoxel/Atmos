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
import net.atmos.diagnostics.*;
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
import net.atmos.render.RenderCluster;
import net.atmos.render.RenderClusterConstructionStage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AtmosClient implements ClientModInitializer {

	private static final FogManager FOG_MANAGER = new FogManager();
	private static final SkyColorController SKY_COLOR_CONTROLLER = new SkyColorController();
	private static final SunGlareController SUN_GLARE_CONTROLLER = new SunGlareController();
	private static final MoonlightController MOONLIGHT_CONTROLLER = new MoonlightController();

	private static final CellGrid CELL_GRID = new CellGrid();
	private static final CellMemoryIntegrator CELL_MEMORY_INTEGRATOR = new CellMemoryIntegrator();
	private static final TelemetryCollector TELEMETRY_COLLECTOR = new TelemetryCollector();

	private static final AtmosphereDirector DIRECTOR = new AtmosphereDirector();
	private static final ExposureModel EXPOSURE_MODEL = new ExposureModel();

	private static FogContext skyContext = null;
	private static float skyDeltaSec = 0f;
	private static long skyLastNanos = -1L;

	private static ResourceKey<Level> currentDimension = null;

	private static KeyMapping diagnosticToggleKey;

	@Override
	public void onInitializeClient() {
		AtmosConfig.load();
		ShaderDetector.init();

		// Register F8 Keybinding to cycle diagnostic modes (OFF -> LIGHT -> NORMAL -> FULL -> OFF)
		diagnosticToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.atmos.toggle_diagnostics",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F8,
				"category.atmos.keys"
		));

		// Handle Mode Toggling via F8 Key
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
		});

		WorldRenderEvents.START.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.cameraEntity == null) return;

			TELEMETRY_COLLECTOR.beginFrame();
			CameraManager.publish(context);
			SKY_COLOR_CONTROLLER.beginFrame();

			DiagnosticManager.beginFrame();

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
				skyContext = null;
				skyLastNanos = -1L;
				skyDeltaSec = 0f;
			}
			currentDimension = newDimension;

			// --- Stage: ENVIRONMENTAL_STATE ---
			DiagnosticHooks.beginStage(PipelineStage.ENVIRONMENTAL_STATE);
			try {
				skyContext = FogContext.capture(mc.gameRenderer.getMainCamera(), mc.level);
				FOG_MANAGER.update(mc.gameRenderer.getMainCamera(), mc.level);
			} finally {
				DiagnosticHooks.endStage(PipelineStage.ENVIRONMENTAL_STATE);
			}

			DiagnosticValidator.validateFogState(
					FOG_MANAGER.getFogStart(), FOG_MANAGER.getFogEnd(),
					FOG_MANAGER.getFogRed(), FOG_MANAGER.getFogGreen(), FOG_MANAGER.getFogBlue(), 1.0f
			);

			// --- Stage: CELL_GRID ---
			DiagnosticHooks.beginStage(PipelineStage.CELL_GRID);
			try {
				CELL_GRID.update(mc.level, mc.gameRenderer.getMainCamera().getBlockPosition());
			} finally {
				DiagnosticHooks.endStage(PipelineStage.CELL_GRID);
			}

			long now = System.nanoTime();
			skyDeltaSec = (skyLastNanos < 0) ? 0f : Math.min((now - skyLastNanos) / 1_000_000_000f, 0.1f);
			skyLastNanos = now;

			CELL_MEMORY_INTEGRATOR.update(CELL_GRID, FOG_MANAGER.getEnvState(), skyDeltaSec);

			runRenderPipeline(mc.level, skyContext, skyDeltaSec);

			TELEMETRY_COLLECTOR.endFrame(CELL_GRID.getActiveCells().size());

			CameraSnapshot cameraSnapshot = CameraManager.get();
			Holder<Biome> biome = mc.level.getBiome(mc.gameRenderer.getMainCamera().getBlockPosition());
			DiagnosticManager.endFrame(
					cameraSnapshot.position().x, cameraSnapshot.position().y, cameraSnapshot.position().z,
					mc.level.getRainLevel(1.0f), mc.level.getThunderLevel(1.0f),
					biome, mc.level.dimension().location().toString()
			);
		});

		// Automatically generate and save diagnostic report when player exits/disconnects from world
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (DiagnosticManager.isActive()) {
				DiagnosticContext ctx = new DiagnosticContext(DiagnosticManager.MODE, DiagnosticClock.currentTimeMillis(), "Atmos-P3");
				String report = BasicReportGenerator.generate(DiagnosticManager.getHistory(), ctx);
				if (DiagnosticManager.MODE == DiagnosticMode.FULL) {
					report += "\n\n" + FullReportGenerator.generateFull(DiagnosticManager.getFullContext());
				}
				DiagnosticFileWriter.writeReport(report);
			}

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
			skyContext = null;
			skyLastNanos = -1L;
			skyDeltaSec = 0f;
			currentDimension = null;
		});
	}

	private static void runRenderPipeline(ClientLevel level, FogContext skyContext, float deltaSec) {
		CameraSnapshot cameraSnapshot = CameraManager.get();
		if (cameraSnapshot == null) return;

		EnvironmentalState env = FOG_MANAGER.getEnvState();
		Holder<Biome> biome = skyContext.biome();
		float sunAngleRadians = skyContext.sunAngle();

		ExposureStateSnapshot exposureSnapshot = ExposureStateManager.get();
		float exposureScale = (exposureSnapshot != null) ? exposureSnapshot.exposureScale() : 1.0f;

		// Record Environmental State Telemetry for FULL Diagnostic Mode
		DiagnosticHooks.recordFullEnvState(
				level.getGameTime(), level.getGameTime(), cameraSnapshot.partialTick(), level.getDayTime(), level.getTimeOfDay(cameraSnapshot.partialTick()),
				skyContext.rain(), skyContext.thunder(), skyContext.sunAngle(),
				FOG_MANAGER.getFogEnd(), FOG_MANAGER.getFogRed(), FOG_MANAGER.getFogGreen(), FOG_MANAGER.getFogBlue(), exposureScale,
				skyContext.biome().unwrapKey().map(k -> k.location().toString()).orElse("Unknown"),
				cameraSnapshot.position().x, cameraSnapshot.position().y, cameraSnapshot.position().z,
				cameraSnapshot.lookDirection().toString()
		);

		// --- Stage: CLUSTER_BUILDER ---
		DiagnosticHooks.beginStage(PipelineStage.CLUSTER_BUILDER);
		List<Cluster> candidates;
		try {
			candidates = ClusterBuilder.build(CELL_GRID, env);
			for (Cluster c : candidates) {
				DiagnosticHooks.recordFullCandidate(
						c.anchorCoord().toString(),
						c.centerWorldPos().x, c.centerWorldPos().y, c.centerWorldPos().z,
						c.radius(), c.radius(), c.cellCount(), 0L, "Flood Fill"
				);
			}
		} finally {
			DiagnosticHooks.endStage(PipelineStage.CLUSTER_BUILDER);
		}
		DiagnosticHooks.recordEventCount(DiagnosticEvent.CLUSTER_GENERATED, candidates.size());

		// --- Stage: COMPOSITION ---
		DiagnosticHooks.beginStage(PipelineStage.COMPOSITION);
		Composition composition;
		try {
			composition = CompositionEngine.compose(new CompositionInputs(candidates, cameraSnapshot, env));
		} finally {
			DiagnosticHooks.endStage(PipelineStage.COMPOSITION);
		}

		int acceptedComps = (composition.heroCluster() != null ? 1 : 0) + composition.secondaryClusters().size() + composition.ambientClusters().size();
		DiagnosticHooks.recordEventCount(DiagnosticEvent.COMPOSITION_ACCEPTED, acceptedComps);
		DiagnosticHooks.recordEventCount(DiagnosticEvent.COMPOSITION_REJECTED, composition.rejectedClusters().size());

		OptimizationPlan optimizationPlan = OptimizationPlanManager.get();
		LocalPlayer player = Minecraft.getInstance().player;
		Vec3 playerPosition = (player != null) ? player.position() : null;

		// --- Stage: DIRECTOR ---
		DiagnosticHooks.beginStage(PipelineStage.DIRECTOR);
		DirectorState directorState;
		try {
			directorState = DIRECTOR.update(new DirectorInputs(
					env, composition, biome, sunAngleRadians, optimizationPlan,
					level.getRainLevel(1.0f), level.getThunderLevel(1.0f), playerPosition
			), deltaSec);
		} finally {
			DiagnosticHooks.endStage(PipelineStage.DIRECTOR);
		}

		// --- Stage: SUN_REACH ---
		DiagnosticHooks.beginStage(PipelineStage.SUN_REACH);
		LightingSnapshot lighting;
		try {
			lighting = AtmosphericLightingPipeline.evaluate(cameraSnapshot, env, directorState, sunAngleRadians);
		} finally {
			DiagnosticHooks.endStage(PipelineStage.SUN_REACH);
		}

		// --- Stage: EXPOSURE ---
		DiagnosticHooks.beginStage(PipelineStage.EXPOSURE);
		try {
			EXPOSURE_MODEL.update(new ExposureInputs(env, CELL_GRID, null, composition, directorState, optimizationPlan, sunAngleRadians), deltaSec);
		} finally {
			DiagnosticHooks.endStage(PipelineStage.EXPOSURE);
		}

		PerformanceSnapshot performanceSnapshot = PerformanceSnapshotBridge.current();

		float gameTimeSeconds = (level.getGameTime() + cameraSnapshot.partialTick()) / 20.0f;
		float rainLevel = skyContext.rain();
		float thunderLevel = skyContext.thunder();

		// --- Stage: RENDER_CLUSTER_CONSTRUCTION ---
		DiagnosticHooks.beginStage(PipelineStage.RENDER_CLUSTER_CONSTRUCTION);
		List<RenderCluster> renderClusters;
		try {
			renderClusters = buildRenderClusters(
					composition, cameraSnapshot, lighting, env, directorState, performanceSnapshot,
					sunAngleRadians, exposureScale, skyContext.renderDistance(), gameTimeSeconds,
					rainLevel, thunderLevel);
		} finally {
			DiagnosticHooks.endStage(PipelineStage.RENDER_CLUSTER_CONSTRUCTION);
		}

		DiagnosticHooks.recordEventCount(DiagnosticEvent.RENDER_CLUSTER_ACCEPTED, renderClusters.size());

		// --- Stage: GEOMETRY_GENERATION ---
		DiagnosticHooks.beginStage(PipelineStage.GEOMETRY_GENERATION);


		// --- Stage: ALSS_RENDERER ---
		DiagnosticHooks.beginStage(PipelineStage.ALSS_RENDERER);
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
}