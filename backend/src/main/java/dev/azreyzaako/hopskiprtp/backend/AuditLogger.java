package dev.azreyzaako.hopskiprtp.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Simple audit logger for backend RTP events.
 */
final class AuditLogger {

    private static final long MAX_BYTES = 1_048_576L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final Path logFile;

    AuditLogger(Path dataDirectory) {
        this.logFile = dataDirectory.resolve("audit.log");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            // Logging must stay best-effort.
        }
    }

    void logTeleport(UUID playerId, String playerName, String worldName, int x, int z, boolean success, String reason) {
        write(String.format("[TELEPORT] %s player=%s(%s) world=%s x=%d z=%d success=%b reason=%s",
            TIMESTAMP.format(Instant.now()), playerName, playerId, worldName, x, z, success, reason));
    }

    void logDenied(UUID playerId, String playerName, String reason) {
        write(String.format("[DENIED]   %s player=%s(%s) reason=%s",
            TIMESTAMP.format(Instant.now()), playerName, playerId, reason));
    }

    private synchronized void write(String line) {
        try {
            rotateIfNecessary();
            Files.writeString(logFile, line + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Audit logging should never break the main flow
        }
    }

    private void rotateIfNecessary() throws IOException {
        if (Files.notExists(logFile)) {
            return;
        }

        if (Files.size(logFile) < MAX_BYTES) {
            return;
        }

        Path rotated = logFile.resolveSibling(logFile.getFileName().toString() + ".1");
        Files.deleteIfExists(rotated);
        Files.move(logFile, rotated, StandardCopyOption.REPLACE_EXISTING);
    }
}
