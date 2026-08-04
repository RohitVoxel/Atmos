package net.atmos.render.gpu;

import java.util.HashMap;
import java.util.Map;

public final class FogGpuCache {

    private final Map<Integer, FogGpuMesh> meshes = new HashMap<>();
    private final GpuUploadScheduler scheduler = new GpuUploadScheduler();

    public GpuUploadScheduler scheduler() { return scheduler; }
    public FogGpuMesh meshFor(int key) { return meshes.get(key); }

    public void reset() {
        for (FogGpuMesh mesh : meshes.values()) mesh.close();
        meshes.clear();
        scheduler.clear();
    }
}