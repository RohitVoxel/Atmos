package net.atmos.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.atmos.cloud.CloudDiagnostics;
import net.atmos.core.AtmosClient;
import net.atmos.overlay.*;
import net.minecraft.client.Minecraft;
import net.atmos.seasonal.SeasonalFeelingSnapshot;
import net.atmos.seasonal.SeasonalFeelingStateManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Read-only reporting commands. Never mutate simulation state. */
public final class DebugCommands {

    private DebugCommands() {}

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var debug = literal("debug");

        debug.then(buildOverlayDebug());
        debug.then(literal("season").executes(ctx -> {
            SeasonalFeelingSnapshot snapshot = SeasonalFeelingStateManager.get();
            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "Atmos Season — current=%s next=%s progress=%.3f strength=%.3f paused=%b overridden=%b",
                    snapshot.calendar().currentSeason(), snapshot.calendar().nextSeason(),
                    snapshot.calendar().seasonProgress(), snapshot.calendar().seasonStrength(),
                    SeasonDebugState.isPaused(), SeasonDebugState.isOverridden())));
            return 1;
        }));
        debug.then(buildCloudDebug());

        root.then(debug);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildOverlayDebug() {
        var overlayDebug = literal("overlay");

        overlayDebug.executes(ctx -> {
            OverlayDiagnostics diag = OverlayDiagnostics.capture(
                    AtmosClient.getOverlayManager(), AtmosClient.getOverlayRenderer(),
                    AtmosClient.getOverlayChunkSurfaceCache());

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  cache: chunks=%d rawFaces=%d positions=%d merged=%d ratio=%.2f largestQuad=%d",
                    diag.cachedChunkCount(), diag.cachedRawSurfaceCount(), diag.cachedPositionCount(),
                    diag.mergedQuadCount(), diag.mergeRatio(), diag.largestMergedQuadArea())));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  updates: dirtyQueue=%d lastBatch=%d rebuilds=%d avgRebuild=%.3fms incremental=%d avgIncremental=%.3fms",
                    diag.dirtyQueueSize(), diag.lastDirtyBatchSize(), diag.chunksRebuilt(),
                    diag.averageFullRebuildNanos() / 1_000_000.0, diag.incrementalUpdatesPerformed(),
                    diag.averageIncrementalNanos() / 1_000_000.0)));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  memory: ~%d KB estimated, invalidEntries=%d",
                    diag.estimatedMemoryBytes() / 1024, diag.invalidCacheEntries())));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "Atmos Overlay Diagnostics — renderer=%s lastRender=%.3fms layers=%d active=%d avg=%.3f",
                    diag.rendererActive() ? "ACTIVE" : "NEVER RENDERED",
                    diag.lastRenderNanos() / 1_000_000.0,
                    diag.lastActiveLayerCount(), diag.activeOverlayCount(), diag.averageOverlayStrength())));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  season=%.3f rain=%.3f registeredTypes=%d cachedTextures=%d",
                    diag.seasonContribution(), diag.rainContribution(),
                    diag.registeredOverlayTypeCount(), diag.cachedTextureCount())));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  faces rendered=%d skipped=%d merged=%d",
                    diag.lastRenderedFaces(), diag.lastSkippedFaces(), diag.lastMergedFaces())));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  chunks cached=%d fullRebuilds=%d avgRebuild=%.3fms incremental=%d avgIncremental=%.3fms",
                    diag.cachedChunkCount(), diag.chunksRebuilt(), diag.averageFullRebuildNanos() / 1_000_000.0,
                    diag.incrementalUpdatesPerformed(), diag.averageIncrementalNanos() / 1_000_000.0)));

            OverlayInvalidationDiagnostics invalidDiag = OverlayInvalidationDiagnostics.capture(
                    AtmosClient.getOverlayChunkSurfaceCache().getTickBatchCollector(),
                    AtmosClient.getOverlayInvalidationQueue(),
                    AtmosClient.getOverlayLevelCrossingScheduler());

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  dirty-pipeline: received=%d merged=%d emitted=%d forcedFlush=%d tracked=%d batches=%d",
                    invalidDiag.eventsReceived(), invalidDiag.eventsMerged(), invalidDiag.positionsEmitted(),
                    invalidDiag.positionsForcedFlushed(), invalidDiag.trackedPositionCount(), invalidDiag.batchesDrained())));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  invalidation queue: size=%d enqueued=%d deduped=%d evicted=%d cancelledByUnload=%d ~%dKB",
                    invalidDiag.queueSize(), invalidDiag.queueEntriesEnqueued(), invalidDiag.queueEntriesDeduplicated(),
                    invalidDiag.queueEntriesEvicted(), invalidDiag.queueCancelledByChunkUnload(),
                    invalidDiag.queueEstimatedMemoryBytes() / 1024)));

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  level crossings: scheduled=%d fired=%d cancelled=%d pending=%d",
                    invalidDiag.scheduledCrossings(), invalidDiag.firedCrossings(),
                    invalidDiag.cancelledCrossings(), invalidDiag.pendingCrossings())));

            if (!diag.missingTextureFamilies().isEmpty()) {
                ctx.getSource().sendFeedback(Component.literal(
                        "  MISSING FAMILIES: " + String.join(", ", diag.missingTextureFamilies())));
            }

            OverlayGpuDiagnostics gpuDiag = OverlayGpuDiagnostics.capture(AtmosClient.getOverlayRenderer().getGpuCache());
            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "  gpu cache: meshes=%d queued=%d building=%d processed=%d stale=%d cancelled=%d avgRebuild=%.3fms avgLatency=%.1fticks",
                    gpuDiag.cachedMeshCount(), gpuDiag.queuedForGpuCount(), gpuDiag.currentlyBuildingCount(),
                    gpuDiag.invalidationsProcessed(), gpuDiag.staleDiscards(), gpuDiag.cancelledRebuilds(),
                    gpuDiag.averageRebuildNanos() / 1_000_000.0, gpuDiag.averageQueueLatencyTicks())));

            OverlayChunkLifecycleDiagnostics lifecycleDiag =
                    OverlayChunkLifecycleDiagnostics.capture(AtmosClient.getOverlayChunkSurfaceCache());
            ctx.getSource().sendFeedback(Component.literal(
                    "  chunk lifecycle: scanQueue=" + lifecycleDiag.scanQueueSize() + " " + lifecycleDiag.counts()));

            for (OverlayType type : OverlayType.values()) {
                float value = AtmosClient.getOverlayManager().getValue(type);
                ctx.getSource().sendFeedback(Component.literal(
                        "  " + type.name() + ": " + value + " (level " + OverlayLevelResolver.levelFor(value)
                                + "/" + OverlayLevelResolver.MAX_LEVEL + ")"));
            }
            return 1;
        });

        overlayDebug.then(literal("rebuild").executes(ctx -> {
            if (Minecraft.getInstance().level != null) {
                AtmosClient.getOverlayChunkSurfaceCache().rebuildAll(Minecraft.getInstance().level);
                ctx.getSource().sendFeedback(Component.literal("Atmos: overlay surface cache rebuilt for all loaded chunks."));
            }
            return 1;
        }));

        return overlayDebug;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCloudDebug() {
        var cloudDebug = literal("clouds");
        cloudDebug.executes(ctx -> {
            CloudDiagnostics diag = CloudDiagnostics.capture(
                    AtmosClient.getCloudManager(), AtmosClient.getCloudRenderer());

            ctx.getSource().sendFeedback(Component.literal(String.format(
                    "Atmos Cloud Diagnostics — initialized=%b renderer=%s lastRender=%.3fms activeLayers=%d textures=%d",
                    diag.initialized(),
                    diag.rendererActive() ? "ACTIVE" : "NEVER RENDERED",
                    diag.lastRenderNanos() / 1_000_000.0,
                    diag.activeLayerCount(), diag.totalTextureCount())));

            if (!diag.missingFamilies().isEmpty()) {
                ctx.getSource().sendFeedback(Component.literal(
                        "  MISSING FAMILIES: " + String.join(", ", diag.missingFamilies())));
            }
            return 1;
        });

        return cloudDebug;
    }
}