package net.atmos.overlay;

import java.util.List;

/**
 * Result of one Overlay resource validation pass — Step 11 of the Overlay
 * Framework completion task. Every field reflects an actual resource-system
 * query performed by {@link OverlayResourceValidator}; nothing here is
 * estimated or assumed.
 */
public record OverlayResourceReport(
        int loadedCount,
        List<String> missingCombinations,
        List<String> invalidResources,
        List<String> duplicateResources,
        List<String> unusedResources
) {
    public boolean isClean() {
        return missingCombinations.isEmpty()
                && invalidResources.isEmpty()
                && duplicateResources.isEmpty()
                && unusedResources.isEmpty();
    }
}