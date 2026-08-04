package net.atmos.render.gpu;

import net.atmos.cloud.CloudLayer;

import java.util.HashMap;
import java.util.Map;

public final class CloudGpuCache {

    private final Map<Integer, CloudGpuMesh> meshes = new HashMap<>();
    private final GpuUploadScheduler scheduler = new GpuUploadScheduler();

    public GpuUploadScheduler scheduler() { return scheduler; }

    public CloudGpuMesh meshFor(int layerIndex) { return meshes.get(layerIndex); }

    public void requestRebuildIfNeeded(CloudLayer layer, float maxExtent, float brightness) {
        int fingerprint = CloudMeshBuilder.fingerprint(layer, maxExtent, brightness);
        CloudGpuMesh existing = meshes.get(layer.index());
        if (existing != null && existing.bakedGeneration() == fingerprint) return;

        scheduler.enqueue(() -> {
            var vertexBuffer = CloudBufferManager.upload(layer, maxExtent, brightness);
            CloudGpuMesh mesh = meshes.computeIfAbsent(layer.index(), CloudGpuMesh::new);
            if (vertexBuffer != null) mesh.assign(vertexBuffer, fingerprint);
        });
    }

    public void reset() {
        for (CloudGpuMesh mesh : meshes.values()) mesh.close();
        meshes.clear();
        scheduler.clear();
    }
}