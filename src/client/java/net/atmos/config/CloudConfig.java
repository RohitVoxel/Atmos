package net.atmos.config;

public final class CloudConfig {

    public boolean cloudsEnabled = true;
    public float   renderDistanceBlocks = 512.0f;
    public float   brightness = 1.0f;
    public boolean debugRendering = false;
    public int     layerCount = 5;

    public float safeRenderDistanceBlocks() {
        return Math.clamp(renderDistanceBlocks, 64.0f, 4096.0f);
    }

    public float safeBrightness() {
        return Math.clamp(brightness, 0.0f, 2.0f);
    }

    public int safeLayerCount() {
        return Math.clamp(layerCount, 1, 5);
    }
}
