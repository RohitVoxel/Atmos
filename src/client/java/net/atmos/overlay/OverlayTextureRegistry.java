package net.atmos.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves overlay atmospheric value -> ResourceLocation.
 *
 * Textures are discovered from the live resource system (Minecraft's
 * ResourceManager) rather than assumed from a fixed naming count. Adding,
 * removing, or renaming files under assets/atmos/textures/overlay/<type>/<res>/
 * requires no code change here — the next cache build (first use, or after
 * a resource reload) picks the new set up automatically, per the Atmos
 * Resource System's discovery contract.
 *
 * Ordering: files are sorted by the trailing numeric suffix in their name
 * (frost_01, frost_02, ... frost_11) rather than plain string order, so an
 * un-padded or double-digit-crossing set still orders correctly. Files with
 * no trailing number sort after all numbered ones, by path.
 */
public final class OverlayTextureRegistry {

    private static final Map<OverlayResolution, Map<OverlayType, List<ResourceLocation>>> CACHE =
            new EnumMap<>(OverlayResolution.class);

    private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d+)(?=\\.[^.]+$)");

    private OverlayTextureRegistry() {}

    /**
     * Returns the texture for the given overlay strength, or {@code null}
     * if no textures were discovered for this type/resolution combination.
     *
     * @param value normalized overlay strength in [0,1]. 0 always resolves
     *              to null — callers should not draw an inactive overlay.
     */
    public static ResourceLocation textureFor(OverlayType type, float value, OverlayResolution resolution) {
        if (value <= 0f) return null;

        List<ResourceLocation> textures = texturesFor(type, resolution);
        if (textures.isEmpty()) return null;

        float clamped = Math.min(1f, Math.max(0f, value));
        int index = Math.min(textures.size() - 1, (int) Math.ceil(clamped * textures.size()) - 1);
        return textures.get(Math.max(0, index));
    }

    public static ResourceLocation textureForLevel(OverlayType type, int level, OverlayResolution resolution) {
        List<ResourceLocation> textures = texturesFor(type, resolution);
        if (textures.isEmpty() || level <= 0) return null;
        int index = Math.min(textures.size() - 1, level - 1);
        return textures.get(Math.max(0, index));
    }

    /** Read-only view of every texture discovered for one type/resolution combo, ordered low-to-high strength. */
    public static List<ResourceLocation> texturesFor(OverlayType type, OverlayResolution resolution) {
        return CACHE
                .computeIfAbsent(resolution, r -> new EnumMap<>(OverlayType.class))
                .computeIfAbsent(type, t -> discover(t, resolution));
    }

    private static List<ResourceLocation> discover(OverlayType type, OverlayResolution resolution) {
        List<ResourceLocation> found = OverlayTextureDiscovery.discover(type, resolution);
        found.sort(Comparator.comparingInt(OverlayTextureRegistry::sortKey).thenComparing(ResourceLocation::getPath));
        return List.copyOf(found);
    }

    private static int sortKey(ResourceLocation location) {
        Matcher matcher = TRAILING_NUMBER.matcher(location.getPath());
        if (!matcher.find()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Invalidated on resource pack reload — see AtmosClient's registered reload listener. */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Total distinct textures currently cached across every discovered type/resolution combo. */
    public static int cachedTextureCount() {
        int total = 0;
        for (Map<OverlayType, List<ResourceLocation>> byType : CACHE.values()) {
            for (List<ResourceLocation> textures : byType.values()) {
                total += textures.size();
            }
        }
        return total;
    }

    static boolean resourceManagerAvailable() {
        return Minecraft.getInstance().getResourceManager() != null;
    }
}