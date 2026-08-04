package net.atmos.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On-demand resource validation for every registered overlay family and
 * resolution — Step 11 of the Overlay Framework completion task.
 *
 * This never runs per-frame. It runs only when explicitly requested
 * (the {@code /atmos debug overlay resources} command), matching the
 * Diagnostics Architecture's "expensive diagnostic work should only occur
 * when advanced diagnostic modes are enabled" principle.
 *
 * Checks performed, using only real resource-system queries:
 *
 *   Loaded    — texture discovered by {@link OverlayTextureDiscovery} and
 *               its resource stream opens successfully.
 *   Missing   — a known OverlayType/OverlayResolution combination with
 *               zero discovered textures.
 *   Invalid   — a discovered ResourceLocation whose resource stream could
 *               not be opened (corrupt entry, packaging error).
 *   Duplicate — the same filename discovered under more than one
 *               namespace for the same type/resolution combination (a
 *               resource pack override is expected and healthy; two
 *               competing non-vanilla-override namespaces is reported).
 *   Unused    — any PNG under textures/overlay/ that does not belong to
 *               any known OverlayType/OverlayResolution combination
 *               (misspelled family, unsupported resolution folder, etc.).
 */
public final class OverlayResourceValidator {

    private OverlayResourceValidator() {}

    public static OverlayResourceReport validate() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        int loaded = 0;

        Set<String> knownPaths = new HashSet<>();

        for (OverlayType type : OverlayType.values()) {
            for (OverlayResolution resolution : OverlayResolution.values()) {
                List<ResourceLocation> textures = OverlayTextureDiscovery.discover(type, resolution);

                if (textures.isEmpty()) {
                    missing.add(type.name().toLowerCase() + " / " + resolution.folder());
                    continue;
                }

                Map<String, Integer> filenameCounts = new HashMap<>();

                for (ResourceLocation location : textures) {
                    knownPaths.add(location.getPath());

                    String fileName = fileNameOf(location.getPath());
                    filenameCounts.merge(fileName, 1, Integer::sum);

                    if (resourceOpens(resourceManager, location)) {
                        loaded++;
                    } else {
                        invalid.add(location.toString());
                    }
                }

                filenameCounts.forEach((fileName, count) -> {
                    if (count > 1) {
                        duplicates.add(type.name().toLowerCase() + "/" + resolution.folder() + "/" + fileName
                                + " (" + count + " competing namespaces)");
                    }
                });
            }
        }

        List<String> unused = findUnusedTextures(resourceManager, knownPaths);

        return new OverlayResourceReport(loaded, missing, invalid, duplicates, unused);
    }

    private static boolean resourceOpens(ResourceManager resourceManager, ResourceLocation location) {
        try {
            var resourceOpt = resourceManager.getResource(location);
            if (resourceOpt.isEmpty()) return false;
            try (var stream = resourceOpt.get().open()) {
                stream.read();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> findUnusedTextures(ResourceManager resourceManager, Set<String> knownPaths) {
        List<String> unused = new ArrayList<>();
        if (resourceManager == null) return unused;

        resourceManager.listResources("textures/overlay", location ->
                "atmos".equals(location.getNamespace()) && location.getPath().endsWith(".png")
        ).keySet().forEach(location -> {
            if (!knownPaths.contains(location.getPath())) {
                unused.add(location.toString());
            }
        });

        return unused;
    }

    private static String fileNameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}