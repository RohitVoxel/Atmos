package net.atmos.cellgrid;

import net.atmos.atmosphere.AtmosphereDrifter;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.memory.MemoryWeights;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * One Atmospheric Cell — a persistent, deterministic record of a small
 * volume of the world's atmosphere, per Chapter 6.
 *
 * Ownership boundary (Appendix C §3, Appendix F §3, Appendix F 2.0 §13.9):
 *
 *   Owned here (procedural/structural, regenerated from deterministic data):
 *     - biome identity
 *     - sky exposure (structural canSeeSky sample at cell center)
 *     - Horizon Map (immutable terrain-visibility profile)
 *     - Canopy Profile (immutable per-slab foliage presence profile,
 *       Chapter 8 §13 Stage Four input — generated and regenerated
 *       identically to Horizon Map; Appendix ZD §5)
 *     - deterministic seed
 *
 *   Owned by the Atmospheric Memory System (Chapter 13), stored here per
 *   Appendix F 2.0 §13.9 ("Historical Data... stored directly within the
 *   local volumes managed by the Cell Grid... must travel with [the cell]"):
 *     - humidity memory
 *     - storm influence
 *     - last memory update tick
 *   Cell Grid stores and streams this data but does not interpret it —
 *   only net.atmos.memory.CellMemoryIntegrator (live advancement) may
 *   mutate it directly. Disk-loaded values reach this class exclusively
 *   through {@link CellGrid}, the sole caller of {@link #absorbLoadedMemory}
 *   (package-private, restricted to net.atmos.cellgrid) — the persistence
 *   service itself never touches a live AtmosCell, only immutable
 *   snapshots, per Appendix F 2.0's Copy-on-Enqueue contract. Light
 *   Residue (also listed by Appendix F 2.0 §13.9) is deferred — see
 *   CellMemoryIntegrator's class doc.
 *
 *   Explicitly NOT owned here — deferred to future systems per the approved
 *   task boundary, and intentionally absent rather than stubbed:
 *     - SunReach evaluation / values           (Chapter 8 evaluators consume
 *                                                horizonMap()/canopyProfile()
 *                                                directly; no cached SunReach
 *                                                value is stored per-cell)
 *     - Confidence values                      (Chapter 4, evaluated ad hoc)
 *     - Illumination values                    (Chapter 6 §24, not yet built)
 *     - Cluster / composition data              (Chapter 7 / 10, cluster-level)
 *
 * Threading: mutable by necessity (Horizon Map and Canopy Profile are
 * replaced on regeneration; LRU touch timestamp updates on every access;
 * Historical Memory advances every frame and may be corrected once by an
 * async disk load), but all mutation is restricted to callers documented
 * on each method. Per Appendix D §11 (Unified Threading Model), Cell Grid
 * is owned exclusively by the Main/Simulation thread — this class is not
 * thread-safe and must not be accessed from the Render Thread or any
 * background thread.
 */
public final class AtmosCell {

    private final CellCoord coord;
    private final long deterministicSeed;

    private Holder<Biome> biome;
    private boolean skyExposed;
    private HorizonMap horizonMap;
    private CanopyProfile canopyProfile;

    // Set externally via CellGrid.markDirty(), cleared once regeneration
    // completes. No automatic trigger is wired to block-update events in
    // this task — the mechanism exists so a future system can invalidate
    // cells without touching CellGrid internals.
    private boolean dirty = false;

    // Monotonic tick counter maintained by CellGrid — not wall-clock time.
    // Drives the cached-tier LRU eviction (LinkedHashMap access order).
    private long lastTouchedTick;

    // --- Historical Memory (Chapter 13 §13.9) ---
    // Owned conceptually by net.atmos.memory; stored here so it streams
    // and caches together with the rest of this cell's lifecycle.
    private final AtmosphereDrifter humidityMemoryDrifter = new AtmosphereDrifter(
            MemoryWeights.CELL_HUMIDITY_MEMORY_DEFAULT,
            MemoryWeights.CELL_HUMIDITY_MEMORY_ACCEL,
            MemoryWeights.CELL_HUMIDITY_MEMORY_DAMP);

    private final AtmosphereDrifter stormInfluenceDrifter = new AtmosphereDrifter(
            MemoryWeights.CELL_STORM_INFLUENCE_DEFAULT,
            MemoryWeights.CELL_STORM_INFLUENCE_ACCEL,
            MemoryWeights.CELL_STORM_INFLUENCE_DAMP);

    private float humidityMemory       = MemoryWeights.CELL_HUMIDITY_MEMORY_DEFAULT;
    private float stormInfluence       = MemoryWeights.CELL_STORM_INFLUENCE_DEFAULT;
    private long  lastMemoryUpdateTick = -1L;

    AtmosCell(CellCoord coord, long deterministicSeed, Holder<Biome> biome,
              boolean skyExposed, HorizonMap horizonMap, CanopyProfile canopyProfile,
              long creationTick) {
        this.coord             = coord;
        this.deterministicSeed = deterministicSeed;
        this.biome             = biome;
        this.skyExposed        = skyExposed;
        this.horizonMap        = horizonMap;
        this.canopyProfile     = canopyProfile;
        this.lastTouchedTick   = creationTick;
    }

    public CellCoord coord()        { return coord; }
    public long deterministicSeed() { return deterministicSeed; }
    public Holder<Biome> biome()    { return biome; }
    public boolean skyExposed()     { return skyExposed; }

    /** Read-only Horizon Map — see Appendix F §3's read-only access contract. */
    public HorizonMap horizonMap()  { return horizonMap; }

    /** Read-only Canopy Profile — Chapter 8 §13 Stage Four input (CanopyOcclusionEvaluator). */
    public CanopyProfile canopyProfile() { return canopyProfile; }

    public boolean isDirty()        { return dirty; }
    public long lastTouchedTick()   { return lastTouchedTick; }

    // --- Historical Memory accessors (Chapter 13 §13.9) ---

    public float humidityMemory()       { return humidityMemory; }
    public float stormInfluence()       { return stormInfluence; }
    public long  lastMemoryUpdateTick() { return lastMemoryUpdateTick; }

    /**
     * Advances this cell's Historical Memory toward {@code humidityTarget}
     * and {@code stormTarget} by {@code deltaSec}, per §13.7's deterministic
     * decay contract. Output is clamped to [0,1] — §13.8's strict saturation
     * bound (AtmosphereDrifter alone permits slight overshoot, appropriate
     * for EnvironmentalState but not for Memory).
     *
     * Exclusively callable by the Atmospheric Memory System
     * (net.atmos.memory.CellMemoryIntegrator). Cell Grid itself never calls
     * this — it owns cell lifecycle, not memory interpretation.
     */
    public void advanceMemory(float humidityTarget, float stormTarget, float deltaSec, long tick) {
        humidityMemory = FogMath.clamp(humidityMemoryDrifter.advance(humidityTarget, deltaSec), 0f, 1f);
        stormInfluence = FogMath.clamp(stormInfluenceDrifter.advance(stormTarget, deltaSec), 0f, 1f);
        lastMemoryUpdateTick = tick;
    }

    /**
     * Applies a persisted {@code CellMemorySnapshot} loaded asynchronously
     * from disk (Appendix F 2.0 §13.9, §13.15). Snaps both drifters
     * directly rather than blending — the same snap-on-authoritative-load
     * idiom already used by {@code EnvironmentalState.snapToTargets} and
     * {@code FogManager}'s first-frame drifter snap.
     *
     * Package-private: callable only from {@code net.atmos.cellgrid}. Its
     * sole actual caller is {@link CellGrid}, which invokes it both when
     * applying a completed async disk load and when reconciling an
     * eviction write against a not-yet-drained load result (Chapter 13
     * §13.15 re-entry, and the corresponding staleness guard on
     * eviction). The persistence service in {@code net.atmos.memory}
     * never calls this directly and never touches a live AtmosCell at
     * all — it only ever hands CellGrid an immutable snapshot to apply,
     * consistent with the Copy-on-Enqueue contract.
     */
    void absorbLoadedMemory(float humidityMemory, float stormInfluence, long tick) {
        this.humidityMemory = FogMath.clamp(humidityMemory, 0f, 1f);
        this.stormInfluence = FogMath.clamp(stormInfluence, 0f, 1f);
        this.humidityMemoryDrifter.snap(this.humidityMemory);
        this.stormInfluenceDrifter.snap(this.stormInfluence);
        this.lastMemoryUpdateTick = tick;
    }

    // --- Package-private mutation, CellGrid-only ---

    void touch(long tick) {
        this.lastTouchedTick = tick;
    }

    void markDirty() {
        this.dirty = true;
    }

    /** Applies freshly regenerated procedural data, then clears the dirty flag. */
    void applyRegeneration(Holder<Biome> biome, boolean skyExposed,
                           HorizonMap horizonMap, CanopyProfile canopyProfile) {
        this.biome         = biome;
        this.skyExposed    = skyExposed;
        this.horizonMap    = horizonMap;
        this.canopyProfile = canopyProfile;
        this.dirty         = false;
    }
}