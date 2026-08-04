package net.atmos.cloud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single owner of every cloud texture. Discovery happens only during
 * {@link #initialize()} — never during rendering. Exposes read-only access
 * grouped by texture family (subfolder name); performs no categorization
 * beyond that grouping, per Batch 1 scope.
 */
public final class CloudTextureRegistry {

    private Map<String, List<ResourceLocation>> texturesByFamily = Map.of();
    private boolean initialized = false;

    public void initialize() {
        texturesByFamily = Collections.unmodifiableMap(
                CloudTextureDiscovery.discoverAll(Minecraft.getInstance().getResourceManager()));
        initialized = true;
    }

    public void reset() {
        texturesByFamily = Map.of();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Set<String> families() {
        return texturesByFamily.keySet();
    }

    public List<ResourceLocation> texturesFor(String family) {
        return texturesByFamily.getOrDefault(family, List.of());
    }

    public int totalTextureCount() {
        int total = 0;
        for (List<ResourceLocation> textures : texturesByFamily.values()) {
            total += textures.size();
        }
        return total;
    }

    /** Read-only diagnostic check — never used to gate rendering logic. */
    public List<String> missingFamilies(List<String> expectedFamilies) {
        List<String> missing = new ArrayList<>();
        for (String family : expectedFamilies) {
            if (texturesFor(family).isEmpty()) {
                missing.add(family);
            }
        }
        return List.copyOf(missing);
    }
}
