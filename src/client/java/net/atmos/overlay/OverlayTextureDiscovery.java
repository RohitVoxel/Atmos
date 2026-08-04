package net.atmos.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every PNG registered under one overlay type/resolution folder,
 * across every namespace and resource pack layered on top of Atmos's own
 * built-in assets — per the Atmos Resource System's resolution order
 * (highest-priority pack first, Atmos built-in last), Minecraft's
 * ResourceManager already applies that layering; this class only reads
 * whatever the manager currently reports.
 *
 * Package-private: the only consumer is {@link OverlayTextureRegistry},
 * which owns caching. This class performs no caching of its own — every
 * call is a fresh resource-system query.
 */
final class OverlayTextureDiscovery {

    private OverlayTextureDiscovery() {}

    private static final String NAMESPACE = "atmos";

    static List<ResourceLocation> discover(OverlayType type, OverlayResolution resolution) {
        List<ResourceLocation> results = new ArrayList<>();

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        if (resourceManager == null) return results;

        String prefix = "textures/overlay/" + type.name().toLowerCase() + "/" + resolution.folder();

        resourceManager.listResources(prefix, location ->
                NAMESPACE.equals(location.getNamespace()) && location.getPath().endsWith(".png")
        ).keySet().forEach(results::add);

        return results;
    }
}