package net.atmos.cellgrid;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * One Atmospheric Cell — a persistent, deterministic record of a small
 * volume of the world's atmosphere, per Chapter 6.
 *
 * Ownership boundary (Appendix C §3, Appendix F §3):
 *
 *   Owned here (procedural/structural, regenerated from deterministic data):
 *     - biome identity
 *     - sky exposure (structural canSeeSky sample at cell center)
 *     - Horizon Map (immutable terrain-visibility profile)
 *     - deterministic seed
 *
 *   Explicitly NOT owned here — deferred to future systems per the approved
 *   task boundary, and intentionally absent rather than stubbed (adding
 *   empty placeholder fields for unbuilt systems would violate the
 *   "no placeholder logic" rule for this task):
 *     - SunReach evaluation / values          (Chapter 8, not yet built)
 *     - Confidence values                     (Chapter 4, not yet built)
 *     - Illumination values                   (Chapter 6 §24, not yet built)
 *     - Cluster / composition data             (Chapter 7 / 10, not yet built)
 *
 * Threading: mutable by necessity (Horizon Map is replaced on regeneration;
 * LRU touch timestamp updates on every access), but all mutation is
 * restricted to package-private methods CellGrid alone calls. Per Appendix D
 * §11 (Unified Threading Model), Cell Grid is owned exclusively by the
 * Main/Simulation thread — this class is not thread-safe and must not be
 * accessed from the Render Thread or any background thread.
 */
public final class AtmosCell {

    private final CellCoord coord;
    private final long deterministicSeed;

    private Holder<Biome> biome;
    private boolean skyExposed;
    private HorizonMap horizonMap;

    // Set externally via CellGrid.markDirty(), cleared once regeneration
    // completes. No automatic trigger is wired to block-update events in
    // this task — the mechanism exists so a future system can invalidate
    // cells without touching CellGrid internals.
    private boolean dirty = false;

    // Monotonic tick counter maintained by CellGrid — not wall-clock time.
    // Drives the cached-tier LRU eviction (LinkedHashMap access order).
    private long lastTouchedTick;

    AtmosCell(CellCoord coord, long deterministicSeed, Holder<Biome> biome,
              boolean skyExposed, HorizonMap horizonMap, long creationTick) {
        this.coord             = coord;
        this.deterministicSeed = deterministicSeed;
        this.biome             = biome;
        this.skyExposed        = skyExposed;
        this.horizonMap        = horizonMap;
        this.lastTouchedTick   = creationTick;
    }

    public CellCoord coord()        { return coord; }
    public long deterministicSeed() { return deterministicSeed; }
    public Holder<Biome> biome()    { return biome; }
    public boolean skyExposed()     { return skyExposed; }

    /** Read-only Horizon Map — see Appendix F §3's read-only access contract. */
    public HorizonMap horizonMap()  { return horizonMap; }

    public boolean isDirty()        { return dirty; }
    public long lastTouchedTick()   { return lastTouchedTick; }

    // --- Package-private mutation, CellGrid-only ---

    void touch(long tick) {
        this.lastTouchedTick = tick;
    }

    void markDirty() {
        this.dirty = true;
    }

    /** Applies freshly regenerated procedural data, then clears the dirty flag. */
    void applyRegeneration(Holder<Biome> biome, boolean skyExposed, HorizonMap horizonMap) {
        this.biome      = biome;
        this.skyExposed = skyExposed;
        this.horizonMap = horizonMap;
        this.dirty      = false;
    }
}