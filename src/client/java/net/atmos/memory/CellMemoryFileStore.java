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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation-defined persistent storage for {@link CellMemorySnapshot}.
 * The Master Guide leaves storage strategy unspecified (Appendix F §6 /
 * Appendix F 2.0 §6 apply the same "implementation-defined, provided
 * architectural contracts hold" freedom here) — one file per cell under
 * {@code config/atmos/memory/<dimension>/} was chosen for simplicity.
 *
 * GZIP satisfies §13.16's "must be strictly lossless" compression
 * requirement. For this payload size (~18 bytes) the GZIP container
 * overhead exceeds the raw payload — this is a known, accepted tradeoff,
 * not a hidden one; see the class doc on
 * {@link AtmosphericMemoryPersistenceService} and the delivery report's
 * Hidden Assumption Audit. A future batched/region-file format (§13.20)
 * is the correct long-term fix and is out of scope here.
 *
 * Writes go through a temp file + atomic move so a crash mid-write can
 * never produce a file that reads back as corrupt (§13.17). Reads treat
 * any unreadable or version-mismatched file as absent rather than as a
 * fatal error, per §13.17's "ignore persisted history... rebuild" policy.
 */
final class CellMemoryFileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atmos/Memory");

    private static final int MAGIC = 0x41544D31; // "ATM1"
    private static final int FORMAT_VERSION = 1;

    private final Path root;

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
                return Optional.empty(); // §13.17 — unrecognized data treated as absent
            }
            float humidityMemory = data.readFloat();
            float stormInfluence = data.readFloat();
            long lastMemoryUpdateTick = data.readLong();

            return Optional.of(new CellMemorySnapshot(
                    dimensionKey, coord, humidityMemory, stormInfluence, lastMemoryUpdateTick));

        } catch (IOException | RuntimeException corruptOrUnreadable) {
            LOGGER.debug("Atmos: discarding unreadable cell memory file {}", file);
            return Optional.empty();
        }
    }

    private void moveIntoPlace(Path tmp, Path file) throws IOException {
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notSupported) {
            // Some filesystems (network shares) reject ATOMIC_MOVE. Still
            // correct, just no longer crash-atomic on those filesystems.
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
            // best-effort cleanup only
        }
    }
}