package net.atmos.cloud;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Discovers every cloud texture under assets/atmos/textures/clouds/, grouped
 * by its immediate subfolder (the texture family). Mirrors
 * OverlayTextureDiscovery's discovery pattern. Package-private — the sole
 * consumer is {@link CloudTextureRegistry}, which owns caching; this class
 * performs no caching of its own.
 */
final class CloudTextureDiscovery {

    private CloudTextureDiscovery() {}

    private static final String NAMESPACE = "atmos";
    private static final String ROOT_PREFIX = "textures/clouds/";

    static Map<String, List<ResourceLocation>> discoverAll(ResourceManager resourceManager) {
        Map<String, List<ResourceLocation>> byFamily = new TreeMap<>();
        if (resourceManager == null) return byFamily;

        resourceManager.listResources("textures/clouds", location ->
                NAMESPACE.equals(location.getNamespace()) && location.getPath().endsWith(".png")
        ).keySet().forEach(location -> {
            String family = familyOf(location.getPath());
            if (family == null) return;
            byFamily.computeIfAbsent(family, f -> new ArrayList<>()).add(location);
        });

        for (List<ResourceLocation> textures : byFamily.values()) {
            textures.sort(Comparator.comparing(ResourceLocation::getPath));
        }

        return byFamily;
    }

    private static String familyOf(String path) {
        if (!path.startsWith(ROOT_PREFIX)) return null;
        String remainder = path.substring(ROOT_PREFIX.length());
        int slash = remainder.indexOf('/');
        if (slash <= 0) return null;
        return remainder.substring(0, slash);
    }
}
