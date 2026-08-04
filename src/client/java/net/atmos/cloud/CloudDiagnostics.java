package net.atmos.cloud;

import java.util.List;

/**
 * Read-only diagnostic snapshot for the cloud system. Every field reflects
 * actual runtime state from {@link CloudManager}/{@link CloudRenderer} —
 * nothing fabricated. No simulation statistics, per Batch 1 scope.
 */
public record CloudDiagnostics(
        boolean initialized,
        boolean rendererActive,
        long lastRenderNanos,
        int activeLayerCount,
        int totalTextureCount,
        List<String> missingFamilies
) {
    public static CloudDiagnostics capture(CloudManager manager, CloudRenderer renderer) {
        List<String> missing = manager.getRegistry().missingFamilies(CloudFamilies.KNOWN_FAMILIES);

        return new CloudDiagnostics(
                manager.isInitialized(),
                renderer.hasRendered(),
                renderer.lastRenderNanos(),
                renderer.lastActiveLayerCount(),
                manager.getRegistry().totalTextureCount(),
                missing
        );
    }
}
