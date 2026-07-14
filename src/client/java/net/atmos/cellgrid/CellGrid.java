package net.atmos.cellgrid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cell Grid — spatial sampling and lifecycle manager for Atmospheric Cells.
 *
 * Implements Chapter 6 (Atmospheric Cell Grid) and the Cell Grid's portion of
 * Appendix F §3 (Horizon Map ownership). Per Appendix D §11 (Unified
 * Threading Model), this class is owned exclusively by the Main/Simulation
 * thread — it performs no rendering, no GPU work, and must never be accessed
 * from the Render Thread concurrently with update().
 *
 * Two-tier storage, per Chapter 6 §12/§16/§33:
 *   - active:  cells within the current streaming radius of the camera.
 *              Rebuilt only when the camera crosses into a new center cell —
 *              movement-gated, matching the existing ValleyFogModifier /
 *              CanopyMoistureModifier cache-threshold idiom already used
 *              throughout the fog pipeline (Permanent Instructions:
 *              "Extend Before Creating").
 *   - cached:  cells recently active but now outside the radius. Kept warm
 *              in a LinkedHashMap (access order) so revisiting a location
 *              reuses existing state instead of regenerating it. Bounded by
 *              MAX_CACHED_CELLS with automatic eviction of the oldest entry
 *              once the cap is exceeded (Appendix B §7).
 *
 * Horizon Map generation cost is bounded because it only runs once per cell
 * on first creation, or once per cell when explicitly marked dirty and next
 * touched — never on a fixed timer, never for cells outside the active radius.
 *
 * Because active/cached both store AtmosCell references (not copies),
 * per-cell Historical Memory (Chapter 13 §13.9, AtmosCell.advanceMemory)
 * automatically travels with a cell across active/cached promotion and
 * demotion — no additional lifecycle handling was required here for that.
 */
public final class CellGrid {

    /** Edge length of one cubic cell, in blocks. Chapter 6 §5 suggests 8 or 16; 16 chosen to match chunk width. */
    public static final int CELL_SIZE = 16;

    /** Horizontal streaming radius, in cells, around the camera's current cell. */
    private static final int HORIZONTAL_RADIUS = 3;

    /** Vertical streaming radius, in cells. Atmosphere varies far less with altitude locally than horizontally. */
    private static final int VERTICAL_RADIUS = 1;

    /** Hard cap on cached (inactive but retained) cells — Appendix B §7. */
    private static final int MAX_CACHED_CELLS = 512;

    private final Map<CellCoord, AtmosCell> active = new HashMap<>();

    private final LinkedHashMap<CellCoord, AtmosCell> cached =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CellCoord, AtmosCell> eldest) {
                    return size() > MAX_CACHED_CELLS;
                }
            };

    // Explicit dirty-invalidation set, per Appendix D §6. Populated only via
    // markDirty() for coordinates not currently active; consumed lazily the
    // next time that coordinate becomes active. No automatic block-event
    // trigger is wired up in this task — see AtmosCell's class doc.
    private final Set<CellCoord> dirtyCoords = new HashSet<>();

    // AUTOPSY cleanup (Task 3): O(1) tracking for whether any *active* cell
    // has been marked dirty directly (the markDirty() branch that bypasses
    // dirtyCoords entirely). Without this flag, regenerateDirtyActiveCells()
    // had no way to know whether a full scan of active.values() was needed
    // other than by performing that scan every single update() call —
    // wasted work today, since markDirty() has no callers yet. Set true
    // only inside markDirty(); cleared once the corresponding scan runs.
    private boolean hasDirtyActiveCell = false;

    // Movement gate: streaming only recomputes when the camera's cell
    // changes, not every tick.
    private CellCoord lastCenterCoord = null;

    private long tickCounter = 0L;

    /**
     * Advances the Cell Grid for the current frame/tick. Cheap no-op unless
     * the camera has moved into a different center cell since the last call,
     * except for a lightweight dirty-cell sweep which always runs.
     *
     * @param level     current client level, used for biome/heightmap sampling.
     * @param cameraPos current camera block position.
     */
    public void update(ClientLevel level, BlockPos cameraPos) {
        tickCounter++;

        CellCoord center = CellCoord.fromWorld(
                cameraPos.getX(), cameraPos.getY(), cameraPos.getZ(), CELL_SIZE);

        if (center.equals(lastCenterCoord)) {
            regenerateDirtyActiveCells(level);
            return;
        }
        lastCenterCoord = center;

        Set<CellCoord> desired = computeDesiredCoords(center);

        // Promote cells into the active set: reuse from cache, or create new.
        for (CellCoord coord : desired) {
            AtmosCell cell = active.get(coord);
            if (cell == null) {
                cell = cached.remove(coord);
                if (cell == null) {
                    cell = createCell(coord, level);
                }
                active.put(coord, cell);
            }
            cell.touch(tickCounter);
        }

        // Demote cells that fell outside the radius into the cache tier.
        var it = active.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!desired.contains(entry.getKey())) {
                AtmosCell demoted = entry.getValue();

                // AUTOPSY Chapter 7 cleanup: if this cell was marked dirty
                // while active but is demoted before
                // regenerateDirtyActiveCells() below has a chance to process
                // it, its dirty state must not be lost. hasDirtyActiveCell
                // is a grid-level aggregate flag — it cannot represent
                // "this specific demoted cell is still dirty," and once
                // regenerateDirtyActiveCells() finishes its next scan (which
                // will no longer see this cell in active.values()), it
                // unconditionally clears that flag. Recording the coordinate
                // in dirtyCoords — the same mechanism already used for cells
                // dirtied while not currently active — guarantees the dirty
                // state survives the cache round-trip and is honored the
                // moment this coordinate becomes active again.
                if (demoted.isDirty()) {
                    dirtyCoords.add(entry.getKey());
                }

                it.remove();
                cached.put(entry.getKey(), demoted);
            }
        }

        regenerateDirtyActiveCells(level);
    }

    /** Returns the loaded cell at the given coordinate, or null if not active. */
    public AtmosCell getCell(CellCoord coord) {
        return active.get(coord);
    }

    /** Read-only view of all currently active cells. */
    public Collection<AtmosCell> getActiveCells() {
        return Collections.unmodifiableCollection(active.values());
    }

    /** Current monotonic simulation tick, per Appendix F 2.0 §13.9 ("Last Update Time"). */
    public long currentTick() {
        return tickCounter;
    }

    /**
     * Returns the active face-adjacent neighbor cells of the given
     * coordinate (Chapter 6 §26). Neighbors outside the active set are
     * silently omitted — callers must not assume a fixed-size result.
     */
    public Collection<AtmosCell> getActiveNeighbors(CellCoord coord) {
        List<AtmosCell> neighbors = new ArrayList<>(6);
        int[][] offsets = {
                { 1, 0, 0}, {-1, 0, 0},
                { 0, 1, 0}, { 0,-1, 0},
                { 0, 0, 1}, { 0, 0,-1},
        };
        for (int[] o : offsets) {
            AtmosCell neighbor = active.get(
                    new CellCoord(coord.x() + o[0], coord.y() + o[1], coord.z() + o[2]));
            if (neighbor != null) neighbors.add(neighbor);
        }
        return neighbors;
    }

    /**
     * Marks the cell at the given coordinate as needing regeneration, per the
     * invalidation path required by Appendix F §3 / Appendix D §6. Safe to
     * call for coordinates that are not currently active — the mark is
     * simply consumed the next time that coordinate becomes active.
     *
     * Nothing in the codebase calls this yet; it exists as the documented
     * extension point for a future terrain-modification hook.
     */
    public void markDirty(CellCoord coord) {
        AtmosCell cell = active.get(coord);
        if (cell != null) {
            cell.markDirty();
            // AUTOPSY cleanup (Task 3): this is the branch that bypasses
            // dirtyCoords — flag it so regenerateDirtyActiveCells() knows a
            // scan is actually required instead of scanning unconditionally.
            hasDirtyActiveCell = true;
        } else {
            dirtyCoords.add(coord);
        }
    }

    /**
     * Clears all cell state. Called from the same lifecycle points as every
     * other Atmos controller's reset() — disconnect and dimension change —
     * so stale terrain/biome data from a previous world/dimension cannot
     * leak into the next one.
     */
    public void reset() {
        active.clear();
        cached.clear();
        dirtyCoords.clear();
        hasDirtyActiveCell = false;
        lastCenterCoord = null;
        tickCounter = 0L;
    }

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    private Set<CellCoord> computeDesiredCoords(CellCoord center) {
        Set<CellCoord> desired = new HashSet<>();
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    desired.add(new CellCoord(center.x() + dx, center.y() + dy, center.z() + dz));
                }
            }
        }
        return desired;
    }

    /**
     * AUTOPSY cleanup (Task 3): early-returns without touching active.values()
     * when there is nothing to regenerate — true on every call today, since
     * markDirty() has no callers. dirtyCoords.isEmpty() alone was previously
     * insufficient to skip the scan, because markDirty() can also flag an
     * already-active cell directly via AtmosCell.markDirty() without ever
     * touching dirtyCoords; hasDirtyActiveCell now covers that path in O(1).
     */
    private void regenerateDirtyActiveCells(ClientLevel level) {
        if (dirtyCoords.isEmpty() && !hasDirtyActiveCell) return;

        for (AtmosCell cell : active.values()) {
            CellCoord coord = cell.coord();
            boolean flaggedExternally = dirtyCoords.remove(coord);
            if (cell.isDirty() || flaggedExternally) {
                regenerate(cell, level);
            }
        }
        hasDirtyActiveCell = false;
    }

    private AtmosCell createCell(CellCoord coord, ClientLevel level) {
        BlockPos centerPos = centerPos(coord);

        Holder<Biome> biome  = level.getBiome(centerPos);
        boolean skyExposed   = level.canSeeSky(centerPos);
        HorizonMap horizonMap = HorizonMapGenerator.generate(coord, CELL_SIZE, level);

        long seed = computeDeterministicSeed(level, coord, biome);

        return new AtmosCell(coord, seed, biome, skyExposed, horizonMap, tickCounter);
    }

    private void regenerate(AtmosCell cell, ClientLevel level) {
        CellCoord coord = cell.coord();
        BlockPos centerPos = centerPos(coord);

        Holder<Biome> biome  = level.getBiome(centerPos);
        boolean skyExposed   = level.canSeeSky(centerPos);
        HorizonMap horizonMap = HorizonMapGenerator.generate(coord, CELL_SIZE, level);

        cell.applyRegeneration(biome, skyExposed, horizonMap);
    }

    private BlockPos centerPos(CellCoord coord) {
        return new BlockPos(
                coord.centerWorldX(CELL_SIZE),
                coord.centerWorldY(CELL_SIZE),
                coord.centerWorldZ(CELL_SIZE));
    }

    /**
     * Combines dimension identity, cell coordinates, and biome identity into
     * a stable long seed, per Chapter 6 §18.
     *
     * Deviation from the architecture text: Chapter 6 §18 specifies
     * "World Seed + Cell Position + Biome Identity." True world seed is not
     * reliably available client-side (ClientLevel does not expose
     * ServerLevel#getSeed()). Since Atmos is explicitly client-side only
     * through V1–V2 (Mod_2_full_architecture.txt), this uses the dimension's
     * resource location as a stable per-world, per-dimension substitute.
     * This still satisfies the architectural intent — identical position +
     * biome always produces an identical seed within a given world/dimension
     * session — it just cannot additionally distinguish two different worlds
     * that happen to share identical terrain, which true world seed would.
     */
    private long computeDeterministicSeed(ClientLevel level, CellCoord coord, Holder<Biome> biome) {
        String dimensionKey = level.dimension().location().toString();
        String biomeKey = biome.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");

        long h = stableHash(dimensionKey);
        h = 31 * h + coord.x();
        h = 31 * h + coord.y();
        h = 31 * h + coord.z();
        h = 31 * h + stableHash(biomeKey);
        return h;
    }

    private static long stableHash(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }
}