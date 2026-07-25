package net.atmos.diagnostics;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles writing diagnostic reports directly to logs/atmos/diagnostic mode/
 * compatible with Prism Launcher directory structures.
 */
public final class DiagnosticFileWriter {

    private DiagnosticFileWriter() {}

    public static synchronized void writeReport(String reportContent) {
        try {
            // Resolves to [GameDirectory]/logs/atmos/diagnostic mode/
            Path logDir = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("atmos").resolve("diagnostic mode");
            Files.createDirectories(logDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
            Path targetFile = logDir.resolve("diagnostic_report_" + timestamp + ".txt");

            Files.writeString(targetFile, reportContent);
            System.out.println("[Atmos Diagnostics] Report successfully saved to: " + targetFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[Atmos Diagnostics] Failed to write diagnostic report file: " + e.getMessage());
        }
    }
}