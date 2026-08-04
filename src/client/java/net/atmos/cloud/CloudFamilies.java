package net.atmos.cloud;

import java.util.List;

/**
 * Reference list of texture family folder names shipped under
 * assets/atmos/textures/clouds/, used only for diagnostics' "missing
 * folders" reporting. CloudTextureRegistry discovers textures dynamically
 * and never depends on this list to function — it exists solely so
 * diagnostics can report an expected family as absent.
 */
public final class CloudFamilies {

    private CloudFamilies() {}

    public static final List<String> KNOWN_FAMILIES = List.of(
            "base", "broken", "cirrus", "clear", "dense", "fogsheet",
            "large", "medium", "noise", "overcast", "puffy", "rain",
            "shadow", "small", "storm", "wispy"
    );
}
