package net.atmos.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional dev-mode watcher: detects atmos.json changes on disk and triggers AtmosReloadManager.reloadAll(). */
public final class AtmosConfigWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Reload");
    private static final long DEBOUNCE_MS = 300L;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile long lastTriggerMs = 0L;

    private AtmosConfigWatcher() {}

    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) return;

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Thread thread = new Thread(() -> watch(configDir), "Atmos-Config-Watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        RUNNING.set(false);
    }

    private static void watch(Path configDir) {
        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            configDir.register(service, StandardWatchEventKinds.ENTRY_MODIFY);

            while (RUNNING.get()) {
                WatchKey key = service.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Object context = event.context();
                    if (context != null && context.toString().equals("atmos.json")) {
                        triggerDebounced();
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.debug("Atmos: config watcher stopped — {}", e.getMessage());
        }
    }

    private static void triggerDebounced() {
        long now = System.currentTimeMillis();
        if (now - lastTriggerMs < DEBOUNCE_MS) return;
        lastTriggerMs = now;
        Minecraft.getInstance().execute(AtmosReloadManager::reloadAll);
    }
}