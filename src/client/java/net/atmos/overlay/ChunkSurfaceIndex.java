package net.atmos.overlay;

import net.atmos.atmosphere.fog.biome.BiomeAtmosphereRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ChunkSurfaceIndex {

    private final ChunkPos chunkPos;
    private final Map<Long, OverlaySurface[]> facesByPos = new HashMap<>();

    private final EnumMap<Direction, List<OverlaySurfaceQuad>> mergedByFace = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Boolean> dirtyByFace = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> versionByFace = new EnumMap<>(Direction.class);

    private final java.util.function.Consumer<BlockPos> onBlockReplaced;
    private final SurfaceInvalidationListener invalidationListener;

    private long generation = 0L;

    ChunkSurfaceIndex(ChunkPos chunkPos, java.util.function.Consumer<BlockPos> onBlockReplaced,
                      SurfaceInvalidationListener invalidationListener) {
        this.chunkPos = chunkPos;
        this.onBlockReplaced = onBlockReplaced;
        this.invalidationListener = invalidationListener;
    }

    long generation() { return generation; }

    void rebuildFull(ClientLevel level) {
        facesByPos.clear();
        markAllDirty();

        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        LevelChunkSection[] sections = chunk.getSections();
        int minBuildHeight = level.getMinBuildHeight();
        int worldX = chunkPos.getMinBlockX();
        int worldZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionMinY = minBuildHeight + sectionIndex * 16;

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int ly = 0; ly < 16; ly++) {
                        probe.set(worldX + lx, sectionMinY + ly, worldZ + lz);
                        scanIfSolid(level, probe);
                    }
                }
            }
        }

        generation++;
        for (Direction dir : Direction.values()) {
            invalidationListener.onSurfaceInvalidated(chunkPos, dir, generation, InvalidationReason.CHUNK_LOADED);
        }
    }

    void updateSinglePosition(ClientLevel level, BlockPos pos) {
        long key = pos.asLong();
        OverlaySurface[] oldFaces = facesByPos.get(key);

        BlockState state = level.getBlockState(pos);
        boolean eligible = OverlayMaterialRegistry.isEligible(state);

        OverlaySurface[] newFaces = eligible ? computeFaces(level, pos, state) : new OverlaySurface[6];

        OverlayMaterial oldMaterial = firstMaterial(oldFaces);
        OverlayMaterial newMaterial = eligible ? OverlayMaterialRegistry.classify(state) : OverlayMaterial.NONE;
        if (oldMaterial != null && oldMaterial != newMaterial) {
            onBlockReplaced.accept(pos);
        }

        boolean anyNew = false;
        for (OverlaySurface f : newFaces) {
            if (f != null) { anyNew = true; break; }
        }

        if (anyNew) facesByPos.put(key, newFaces);
        else facesByPos.remove(key);

        for (Direction dir : Direction.values()) {
            OverlaySurface o = oldFaces == null ? null : oldFaces[dir.ordinal()];
            OverlaySurface n = newFaces[dir.ordinal()];
            if (!facesEquivalent(o, n)) {
                markFaceDirtyIncremental(dir);
            }
        }
    }

    void advanceSimulation(OverlayAccumulationSimulation simulation, OverlayEnvironmentalContext ctx, long currentTick) {
        for (Map.Entry<Long, OverlaySurface[]> entry : facesByPos.entrySet()) {
            OverlaySurface[] faces = entry.getValue();

            OverlaySurface representative = null;
            for (OverlaySurface f : faces) {
                if (f != null && f.face() == Direction.UP) { representative = f; break; }
            }
            if (representative == null) {
                for (OverlaySurface f : faces) { if (f != null) { representative = f; break; } }
            }
            if (representative == null) continue;

            int faceMask = 0;
            for (int i = 0; i < faces.length; i++) {
                if (faces[i] != null) faceMask |= (1 << i);
            }

            OverlayMaterial material = OverlayMaterialRegistry.classify(representative.blockState());
            simulation.advance(representative, faceMask, material, ctx, currentTick);
        }
    }

    private static OverlayMaterial firstMaterial(OverlaySurface[] faces) {
        if (faces == null) return null;
        for (OverlaySurface f : faces) {
            if (f != null) return OverlayMaterialRegistry.classify(f.blockState());
        }
        return null;
    }

    private void scanIfSolid(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!OverlayMaterialRegistry.isEligible(state)) return;

        OverlaySurface[] faces = computeFaces(level, pos, state);
        for (OverlaySurface f : faces) {
            if (f != null) {
                facesByPos.put(pos.asLong(), faces);
                return;
            }
        }
    }

    private static OverlaySurface[] computeFaces(ClientLevel level, BlockPos pos, BlockState state) {
        OverlaySurface[] faces = new OverlaySurface[6];
        for (Direction dir : Direction.values()) {
            if (isExposedFace(level, pos.relative(dir), dir)) {
                faces[dir.ordinal()] = buildSurface(level, pos, dir, state);
            }
        }
        return faces;
    }

    private static boolean isExposedFace(ClientLevel level, BlockPos neighborPos, Direction fromDir) {
        BlockState neighbor = level.getBlockState(neighborPos);
        if (!neighbor.getFluidState().isEmpty()) return true;
        if (neighbor.isAir()) return true;
        return !neighbor.isFaceSturdy(level, neighborPos, fromDir.getOpposite());
    }

    private static OverlaySurface buildSurface(ClientLevel level, BlockPos pos, Direction dir, BlockState state) {
        boolean skyVisible = level.canSeeSky(pos.above());
        float exposure = ExposureEvaluator.evaluate(level, pos, dir, skyVisible);

        Holder<Biome> biomeHolder = level.getBiome(pos);
        float temperature = biomeHolder.value().getBaseTemperature();
        float humidity = BiomeAtmosphereRegistry.of(biomeHolder).fog().humidity();
        float rainfall = level.getRainLevel(1.0f);

        return new OverlaySurface(pos, dir, state, skyVisible, exposure, temperature, humidity, rainfall);
    }

    private static boolean facesEquivalent(OverlaySurface a, OverlaySurface b) {
        if (a == null || b == null) return a == b;
        return a.blockState().equals(b.blockState())
                && Math.abs(a.exposure() - b.exposure()) < 0.01f;
    }

    private void markAllDirty() {
        for (Direction dir : Direction.values()) {
            markFaceDirty(dir);
        }
    }

    private void markFaceDirty(Direction dir) {
        dirtyByFace.put(dir, Boolean.TRUE);
        versionByFace.merge(dir, 1, Integer::sum);
    }

    private void markFaceDirtyIncremental(Direction dir) {
        markFaceDirty(dir);
        generation++;
        invalidationListener.onSurfaceInvalidated(chunkPos, dir, generation, InvalidationReason.BLOCK_CHANGED);
    }

    List<OverlaySurfaceQuad> mergedQuads(Direction face, ClientLevel level) {
        if (Boolean.TRUE.equals(dirtyByFace.get(face))) {
            mergedByFace.put(face, buildMergedQuads(face, level));
            dirtyByFace.put(face, Boolean.FALSE);
        }
        return mergedByFace.getOrDefault(face, List.of());
    }

    private List<OverlaySurfaceQuad> buildMergedQuads(Direction face, ClientLevel level) {
        Map<Integer, LayerBuilder> layers = new HashMap<>();

        for (Map.Entry<Long, OverlaySurface[]> entry : facesByPos.entrySet()) {
            OverlaySurface surface = entry.getValue()[face.ordinal()];
            if (surface == null) continue;

            BlockPos pos = BlockPos.of(entry.getKey());
            int layerKey;
            int a;
            int b;

            switch (face.getAxis()) {
                case Y -> { layerKey = pos.getY(); a = pos.getX() & 15; b = pos.getZ() & 15; }
                case Z -> { layerKey = pos.getZ(); a = pos.getX() & 15; b = pos.getY(); }
                default -> { layerKey = pos.getX(); a = pos.getZ() & 15; b = pos.getY(); }
            }

            layers.computeIfAbsent(layerKey, k -> new LayerBuilder()).put(a, b, pos, surface);
        }

        List<OverlaySurfaceQuad> result = new ArrayList<>();
        for (LayerBuilder layer : layers.values()) {
            for (OverlaySurfaceQuad q : layer.merge(face)) {
                int light = LevelRenderer.getLightColor(level, q.origin());
                result.add(q.withLight(light));
            }
        }
        return result;
    }

    int rawFaceCount() {
        int total = 0;
        for (OverlaySurface[] faces : facesByPos.values()) {
            for (OverlaySurface f : faces) if (f != null) total++;
        }
        return total;
    }

    int cachedPositionCount() {
        return facesByPos.size();
    }

    private static final class LayerBuilder {
        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE;
        int minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
        final Map<Long, CellEntry> cells = new HashMap<>();

        void put(int a, int b, BlockPos pos, OverlaySurface surface) {
            if (a < minA) minA = a;
            if (a > maxA) maxA = a;
            if (b < minB) minB = b;
            if (b > maxB) maxB = b;
            cells.put(packKey(a, b), new CellEntry(pos, surface));
        }

        List<OverlaySurfaceQuad> merge(Direction face) {
            int sizeA = maxA - minA + 1;
            int sizeB = maxB - minB + 1;
            int offsetA = minA, offsetB = minB;

            return FaceMeshBuilder.merge(face, sizeA, sizeB,
                    (a, b) -> {
                        CellEntry e = cells.get(packKey(a + offsetA, b + offsetB));
                        return e == null ? null : new FaceMeshBuilder.FaceCellData(e.pos(), e.surface());
                    });
        }

        private static long packKey(int a, int b) {
            return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
        }
    }

    private record CellEntry(BlockPos pos, OverlaySurface surface) {}
}