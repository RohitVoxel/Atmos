package net.atmos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AtmosConfig {

    private static final Logger LOGGER   = LoggerFactory.getLogger("Atmos/Config");
    private static final Gson   GSON     = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "atmos.json";

    private static volatile AtmosConfig INSTANCE = new AtmosConfig();

    public FogConfig       fog      = new FogConfig();
    public DebugConfig     debug    = new DebugConfig();
    public SkyPhaseConfig  skyPhase = new SkyPhaseConfig();
    public AirConfig       air      = new AirConfig();
    public SeasonalConfig  seasonal = new SeasonalConfig();
    public OverlayConfig   overlay  = new OverlayConfig();
    public CloudConfig     cloud    = new CloudConfig();

    public static void load() {
        Path path = configPath();

        if (INSTANCE.seasonal == null) INSTANCE.seasonal = new SeasonalConfig();
        if (INSTANCE.overlay  == null) INSTANCE.overlay  = new OverlayConfig();
        if (INSTANCE.cloud == null) INSTANCE.cloud = new CloudConfig();

        if (!Files.exists(path)) {
            LOGGER.info("Atmos config not found — writing defaults to {}", path);
            INSTANCE = new AtmosConfig();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            AtmosConfig loaded = GSON.fromJson(reader, AtmosConfig.class);
            INSTANCE = (loaded != null) ? loaded : new AtmosConfig();
            if (INSTANCE.fog      == null) INSTANCE.fog      = new FogConfig();
            if (INSTANCE.debug    == null) INSTANCE.debug    = new DebugConfig();
            if (INSTANCE.skyPhase == null) INSTANCE.skyPhase = new SkyPhaseConfig();
            if (INSTANCE.air      == null) INSTANCE.air      = new AirConfig();
            if (INSTANCE.seasonal == null) INSTANCE.seasonal = new SeasonalConfig();
            if (INSTANCE.overlay  == null) INSTANCE.overlay  = new OverlayConfig();
            LOGGER.info("Atmos config loaded from {}", path);
        } catch (IOException e) {
            LOGGER.warn("Failed to read Atmos config — using defaults. Reason: {}", e.getMessage());
            INSTANCE = new AtmosConfig();
        }
    }

    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save Atmos config. Reason: {}", e.getMessage());
        }
    }

    public static AtmosConfig get() { return INSTANCE; }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
    }
}
