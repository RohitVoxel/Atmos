package net.atmos.core;

import net.atmos.developer.DvfManager;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogManager;
import net.atmos.atmosphere.sky.MoonlightController;
import net.atmos.atmosphere.sky.SkyColorController;
import net.atmos.atmosphere.sky.SunGlareController;
import net.atmos.cellgrid.CellGrid;
import net.atmos.compat.ShaderDetector;
import net.atmos.config.AtmosConfig;
import net.atmos.memory.CellMemoryIntegrator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class AtmosClient implements ClientModInitializer {

	private static final FogManager               FOG_MANAGER           = new FogManager();
	private static final SkyColorController        SKY_COLOR_CONTROLLER  = new SkyColorController();
	private static final SunGlareController        SUN_GLARE_CONTROLLER  = new SunGlareController();
	private static final MoonlightController       MOONLIGHT_CONTROLLER  = new MoonlightController();

	// Forest Spec Task 1 — Crepuscular Rays. Owns a small position cache for
	// its sky-visibility check (added in the review fix pass), so unlike a
	// purely stateless calculation it needs the same reset() treatment as
	// FOG_MANAGER/SKY_COLOR_CONTROLLER below — see both lifecycle points.
	// intensity/red/green/blue/sunHeight/sinAngle are still fully
	// recomputed every frame regardless; only the position cache persists.

	// Cell Grid (Chapter 6 / Appendix F §3). Owns spatial cell lifecycle and
	// Horizon Map generation only — no SunReach, Confidence, Illumination,
	// or Cluster data. Driven once per frame here, same pattern as every
	// other controller in this class.
	//
	// Lifecycle split (audit Finding F fix): dimension change calls
	// CELL_GRID.reset() (flush only — persistence must stay alive for the
	// next dimension). DISCONNECT calls CELL_GRID.shutdown() (flush, then
	// bounded drain-and-stop of the persistence layer's background
	// thread) — see both handlers below.
	private static final CellGrid CELL_GRID = new CellGrid();

	// Chapter 13 Stage 2/4 — per-cell Historical Memory advancement
	// (net.atmos.memory.CellMemoryIntegrator). Reads EnvironmentalState via
	// FOG_MANAGER.getEnvState() (Extend Before Creating — no new
	// environmental source introduced). Owned independently of CellGrid,
	// matching every other controller in this class; CellGrid itself never
	// calls it (Chapter 13 §13.9 ownership boundary — see AtmosCell's
	// class doc). No OptimizationPlan producer exists yet (Chapter 16 is
	// unbuilt), so the unscaled overload is used, matching
	// DirectorPerformanceEvaluator's null-safe failsafe precedent.
	private static final CellMemoryIntegrator CELL_MEMORY_INTEGRATOR = new CellMemoryIntegrator();

	// skyContext is written once per frame at WorldRenderEvents.START and read
	// by SkyMixin. Nulled on disconnect and dimension change so SkyMixin's
	// null check catches the gap before the next valid frame.
	private static FogContext skyContext = null;

	// Delta time for sky color smoothing.
	// Computed once per frame at WorldRenderEvents.START.
	// Capped at 0.1s to protect against alt-tab and load spikes.
	private static float skyDeltaSec  = 0f;
	private static long  skyLastNanos = -1L;

	// Dimension tracking for mid-session change detection.
	// Portal travel does not fire DISCONNECT — we must detect it here.
	// Null on startup and after disconnect so the first frame always
	// initialises the dimension key without triggering a spurious reset.
	private static ResourceKey<Level> currentDimension = null;

	@Override
	public void onInitializeClient() {
		AtmosConfig.load();
		ShaderDetector.init();
		DvfManager.init();

		WorldRenderEvents.START.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.cameraEntity == null) return;

			// Publish the per-frame CameraSnapshot first — every other system
			// that reads camera state this frame (currently none; future:
			// Confidence, Exposure, PES) depends on this being fresh before
			// they run. Per Appendix F §1, this is the single Render Thread
			// writer call site.
			//
			// NOTE: this snapshot must remain valid for the rest of the
			// frame, including the dimension-change branch below. Do not
			// call CameraManager.reset() anywhere after this point within
			// the same frame — see CameraManager's class doc.
			CameraManager.publish(context);

			// Reset the sky drifter advance guard at the start of each frame.
			// Must be called before any getSkyColor() can fire — WorldRenderEvents.START
			// is the earliest safe point in the render pipeline.
			SKY_COLOR_CONTROLLER.beginFrame();

			// --- Dimension change detection ---
			// Portal travel changes mc.level without firing DISCONNECT.
			// Detect the dimension key mismatch and reset all atmospheric
			// state before capturing the new context — prevents Overworld
			// drifter values from bleeding into the Nether/End and vice versa.
			//
			// CameraManager.reset() is intentionally NOT called here. The
			// CameraSnapshot published above this block is still a valid,
			// fully-formed snapshot of the current camera for this frame —
			// dimension change does not invalidate camera geometry, only
			// world/environmental state. Resetting it here would destroy
			// the frame's just-published snapshot for no correctness reason.
			// CameraManager.reset() is reserved for genuine teardown
			// (disconnect) — see the DISCONNECT handler below.
			//
			// CELL_GRID.reset() (not shutdown()) is deliberate here: the
			// persistence layer's background thread must remain alive for
			// the destination dimension. reset() still flushes the
			// outgoing dimension's Historical Data before clearing.
			ResourceKey<Level> newDimension = mc.level.dimension();
			if (currentDimension != null && !currentDimension.equals(newDimension)) {
				FOG_MANAGER.reset();
				SKY_COLOR_CONTROLLER.reset();
				CELL_GRID.reset();
				CELL_MEMORY_INTEGRATOR.reset();
				skyContext   = null;
				skyLastNanos = -1L;
				skyDeltaSec  = 0f;
				// FogManager.reset() already calls FogContext.clearBiomeCache()
				// so the stale Overworld dominant-biome reference is cleared.
			}
			currentDimension = newDimension;

			// Capture sky context and advance environmental state together at
			// render start — before getSkyColor() can be called by the vanilla
			// renderer. Guarantees skyContext and envState are always from the
			// same frame moment.
			//
			// FogManager.update() is safe to call here because its UPDATE_GUARD_NS
			// (2ms) will block the redundant call from FogMixin.setupFog() later
			// in the same frame — no double update, no side effects.
			skyContext = FogContext.capture(mc.gameRenderer.getMainCamera(), mc.level);
			FOG_MANAGER.update(mc.gameRenderer.getMainCamera(), mc.level);

			// Cell Grid: movement-gated internally (no-op unless the camera has
			// crossed into a new center cell since the last call), so calling
			// it unconditionally every frame is cheap — same pattern as every
			// other controller above.
			CELL_GRID.update(mc.level, mc.gameRenderer.getMainCamera().getBlockPosition());

			// Crepuscular ray intensity reads the same frame-fresh skyContext
			// and envState as everything else above — single per-frame call
			// site, same pattern as FOG_MANAGER.update(). Openness is the
			// already hysteresis-blended value (review Fix 2) — FOG_MANAGER
			// has already run its update() above this line, so renderState
			// (and therefore getFogOpenness()) is fresh for this frame.
			//
			// Fog RGB passed so CrepuscularRayController can derive ray color
			// from the current atmospheric state rather than a fixed warm-gold.
			// These are the same pipeline-smoothed values the fog system
			// renders — they already encode dawn warmth, storm grey, rain
			// desaturation via the full modifier pipeline.


			long now     = System.nanoTime();
			skyDeltaSec  = (skyLastNanos < 0) ? 0f
					: Math.min((now - skyLastNanos) / 1_000_000_000f, 0.1f);
			skyLastNanos = now;

			// Chapter 13 §13.9/§13.18 — per-cell Historical Memory
			// advancement. Placed after skyDeltaSec is (re)computed above
			// so this uses the current frame's delta rather than the
			// previous frame's stale value.
			CELL_MEMORY_INTEGRATOR.update(CELL_GRID, FOG_MANAGER.getEnvState(), skyDeltaSec);
		});

		// Reset all atmospheric state on disconnect.
		// Dimension tracking cleared so the next session's first frame
		// initialises cleanly without a spurious reset.
		//
		// CELL_GRID.shutdown() (audit Finding F fix, replacing the
		// previous plain reset()): disconnect is genuine session
		// teardown, not just a state reset — it flushes exactly as
		// reset() does, then gives the persistence layer's background
		// thread a bounded grace period to finish writing before it is
		// stopped, rather than leaving that work to an abruptly-killed
		// daemon thread if the client process exits shortly after. A
		// fresh persistence service is installed internally so a later
		// reconnect within the same client session still works.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FOG_MANAGER.reset();
			SKY_COLOR_CONTROLLER.reset();
			CameraManager.reset();
			CELL_GRID.shutdown();
			CELL_MEMORY_INTEGRATOR.reset();
			skyContext        = null;
			skyLastNanos      = -1L;
			skyDeltaSec       = 0f;
			currentDimension  = null;
		});
	}

	public static FogManager               getFogManager()               { return FOG_MANAGER;               }
	public static SkyColorController       getSkyColorController()       { return SKY_COLOR_CONTROLLER;      }
	public static SunGlareController       getSunGlareController()       { return SUN_GLARE_CONTROLLER;      }
	public static MoonlightController      getMoonlightController()      { return MOONLIGHT_CONTROLLER;      }
	public static CellGrid                 getCellGrid()                 { return CELL_GRID;                 }

	public static FogContext getSkyContext()   { return skyContext;  }
	public static float      getSkyDeltaSec() { return skyDeltaSec; }
}