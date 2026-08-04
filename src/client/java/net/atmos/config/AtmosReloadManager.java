package net.atmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Sole coordinator: reloads AtmosConfig from disk, then notifies every registered system in order. */
public final class AtmosReloadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Reload");
    private static final List<AtmosReloadable> REGISTERED = new CopyOnWriteArrayList<>();

    private AtmosReloadManager() {}

    public static void register(AtmosReloadable reloadable) {
        if (reloadable == null) throw new IllegalArgumentException("reloadable must not be null");
        REGISTERED.add(reloadable);
    }

    public static void unregister(AtmosReloadable reloadable) {
        REGISTERED.remove(reloadable);
    }

    public static void reloadAll() {
        AtmosConfig.load();
        for (AtmosReloadable reloadable : REGISTERED) {
            try {
                reloadable.onConfigReload();
            } catch (RuntimeException e) {
                LOGGER.warn("Atmos: reload listener failed — {}", e.getMessage());
            }
        }
        LOGGER.info("Atmos: configuration reloaded ({} systems notified)", REGISTERED.size());
    }
}