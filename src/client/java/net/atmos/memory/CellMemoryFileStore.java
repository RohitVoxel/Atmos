package net.atmos.memory;

import net.atmos.cellgrid.CellCoord;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation-defined persistent storage for {@link CellMemorySnapshot}.
 * See prior class doc for storage-format rationale (unchanged).
 *
 * Chapter 13 §13.17 (Failure Recovery): every rejection path below —
 * format mismatch, invalid payload, or IO failure — is treated
 * identically as "absent," per §13.17's "ignore persisted history"
 * directive. {@link #corruptedReadsDetected} tracks how often this
 * occurs, surfaced via {@link AtmosphericMemoryPersistenceService#diagnostics()}.
 */
final class CellMemoryFileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Memory");

    private static final int MAGIC = 0x41544D31; // "ATM1"
    private static final int FORMAT_VERSION = 1;

    private final Path root;
    private final AtomicLong corruptedReadsDetected = new AtomicLong();

    CellMemoryFileStore() {
        this.root = FabricLoader.getInstance().getConfigDir().resolve("atmos").resolve("memory");
    }

    /** Runs entirely on the calling (background IO) thread. Never invoked from the Simulation Thread. */
    void write(CellMemorySnapshot snapshot) {
        Path file = pathFor(snapshot.dimensionKey(), snapshot.coord());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            Files.createDirectories(file.getParent());
            try (OutputStream fileOut = Files.newOutputStream(tmp);
                 GZIPOutputStream gzip = new GZIPOutputStream(fileOut);
                 DataOutputStream data = new DataOutputStream(gzip)) {
                data.writeInt(MAGIC);
                data.writeInt(FORMAT_VERSION);
                data.writeFloat(snapshot.humidityMemory());
                data.writeFloat(snapshot.stormInfluence());
                data.writeLong(snapshot.lastMemoryUpdateTick());
            }
            moveIntoPlace(tmp, file);
        } catch (IOException e) {
            LOGGER.debug("Atmos: failed to persist cell memory for {} — {}", snapshot.coord(), e.getMessage());
            deleteQuietly(tmp);
        }
    }

    /** Runs entirely on the calling (background IO) thread. Never invoked from the Simulation Thread. */
    Optional<CellMemorySnapshot> read(String dimensionKey, CellCoord coord) {
        Path file = pathFor(dimensionKey, coord);
        if (!Files.exists(file)) return Optional.empty();

        try (InputStream fileIn = Files.newInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(fileIn);
             DataInputStream data = new DataInputStream(gzip)) {

            int magic = data.readInt();
            int version = data.readInt();
            if (magic != MAGIC || version != FORMAT_VERSION) {
                corruptedReadsDetected.incrementAndGet();
                return Optional.empty(); // §13.17 — unrecognized data treated as absent
            }
            float humidityMemory = data.readFloat();
            float stormInfluence = data.readFloat();
            long lastMemoryUpdateTick = data.readLong();

            if (!isValidMemoryValue(humidityMemory) || !isValidMemoryValue(stormInfluence)) {
                corruptedReadsDetected.incrementAndGet();
                LOGGER.debug("Atmos: discarding invalid cell memory payload for {}", coord);
                return Optional.empty(); // §13.17 — invalid persisted memory ignored
            }

            return Optional.of(new CellMemorySnapshot(
                    dimensionKey, coord, humidityMemory, stormInfluence, lastMemoryUpdateTick));

        } catch (IOException | RuntimeException corruptOrUnreadable) {
            corruptedReadsDetected.incrementAndGet();
            LOGGER.debug("Atmos: discarding unreadable cell memory file {}", file);
            return Optional.empty();
        }
    }

    /** Cross-thread counter read (IO thread writes, Simulation Thread reads via diagnostics()). */
    long corruptedReadsDetected() {
        return corruptedReadsDetected.get();
    }

    private static boolean isValidMemoryValue(float v) {
        return Float.isFinite(v) && v >= 0f && v <= 1f;
    }

    private void moveIntoPlace(Path tmp, Path file) throws IOException {
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notSupported) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path pathFor(String dimensionKey, CellCoord coord) {
        String safeDim = dimensionKey.replace(':', '_').replace('/', '_');
        String fileName = coord.x() + "_" + coord.y() + "_" + coord.z() + ".dat";
        return root.resolve(safeDim).resolve(fileName);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}