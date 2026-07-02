package net.atmos.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class ShaderDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Compat");

    private static boolean initialized     = false;
    private static boolean irisPresent     = false;
    private static boolean canvasPresent   = false;
    private static boolean sodiumPresent   = false;
    private static boolean optifinePresent = false;

    // Cached reflection handles for isIrisShaderActive().
    // Reflection every render call is expensive. We cache the method reference
    // and instance after the first successful lookup.
    private static Object  irisApiInstance  = null;
    private static Method  irisShaderMethod = null;
    private static boolean irisReflectReady = false;

    // isIrisShaderActive() result is cached for one frame and refreshed at
    // the start of each logical update. Shader pack state doesn't change
    // mid-frame and the render thread doesn't need to re-check every pass.
    private static boolean cachedShaderActive       = false;
    private static long    cacheUpdatedAtNanos      = -1L;
    private static final long CACHE_TTL_NS          = 16_000_000L; // ~1 frame at 60fps

    public static void init() {
        if (initialized) return;

        FabricLoader loader = FabricLoader.getInstance();
        irisPresent      = loader.isModLoaded("iris");
        canvasPresent    = loader.isModLoaded("canvas");
        sodiumPresent    = loader.isModLoaded("sodium");
        optifinePresent  = loader.isModLoaded("optifine");
        initialized      = true;

        if (irisPresent)     LOGGER.info("Atmos: Iris detected.");
        if (canvasPresent)   LOGGER.info("Atmos: Canvas detected.");
        if (sodiumPresent)   LOGGER.info("Atmos: Sodium detected.");
        if (optifinePresent) LOGGER.warn("Atmos: OptiFine detected — compatibility not guaranteed.");
        if (!irisPresent && !canvasPresent && !sodiumPresent && !optifinePresent)
            LOGGER.info("Atmos: No shader/rendering mods detected. Running vanilla path.");

        // Pre-cache reflection handles if Iris is present.
        if (irisPresent) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApiInstance  = api.getMethod("getInstance").invoke(null);
                irisShaderMethod = irisApiInstance.getClass().getMethod("isShaderPackInUse");
                irisReflectReady = true;
                LOGGER.info("Atmos: Iris API reflection cached successfully.");
            } catch (Exception e) {
                LOGGER.warn("Atmos: Could not cache Iris API reflection — shader detection disabled. {}", e.getMessage());
                irisReflectReady = false;
            }
        }
    }

    public static boolean isIrisPresent()     { return irisPresent;     }
    public static boolean isCanvasPresent()   { return canvasPresent;   }
    public static boolean isSodiumPresent()   { return sodiumPresent;   }
    public static boolean isOptiFinePresent() { return optifinePresent; }

    /**
     * Returns whether an Iris shader pack is currently active.
     * Result is cached per frame — the render thread calls this from both
     * setupFog and setupColor, so we avoid redundant reflection invocations.
     */
    public static boolean isIrisShaderActive() {
        if (!irisPresent || !irisReflectReady) return false;

        long now = System.nanoTime();
        if (cacheUpdatedAtNanos >= 0 && (now - cacheUpdatedAtNanos) < CACHE_TTL_NS) {
            return cachedShaderActive;
        }

        try {
            cachedShaderActive  = (boolean) irisShaderMethod.invoke(irisApiInstance);
            cacheUpdatedAtNanos = now;
        } catch (Exception e) {
            // If the Iris API becomes unavailable mid-session, fail safe.
            cachedShaderActive = false;
        }

        return cachedShaderActive;
    }

    public static boolean hasConflictingRenderer() {
        return canvasPresent || isIrisShaderActive();
    }
}