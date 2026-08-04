package net.atmos.render;

import net.atmos.cluster.Cluster;
import net.atmos.composition.Composition;
import net.atmos.director.DirectorState;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Batch 1 Phase 0 — publishes the last tick-computed pipeline output for the
 * render thread to read. The render callback (WorldRenderEvents.START/
 * AFTER_TRANSLUCENT) never computes this itself; it only reads whatever was
 * last published here, at 20 TPS cadence regardless of render FPS.
 *
 * Mirrors the existing CameraManager/ExposureStateManager lock-free
 * publish/get pattern already used throughout Atmos.
 */
public final class RenderPipelineCache {

    private RenderPipelineCache() {}

    public record Snapshot(
            List<Cluster> clusters,
            Composition composition,
            DirectorState directorState,
            List<RenderCluster> renderClusters
    ) {}

    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(null);

    public static void publish(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        CURRENT.set(snapshot);
    }

    public static Snapshot get() {
        return CURRENT.get();
    }

    public static void reset() {
        CURRENT.set(null);
    }
}