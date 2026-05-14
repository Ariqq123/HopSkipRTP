package dev.azreyzaako.hopskiprtp.proxy;

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
 * Simple audit logger for RTP events.
 * Writes to a rotating log file in the plugin data directory.
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

    void logRequest(UUID playerId, String playerName, String targetServer, boolean allowed) {
        write(String.format("[REQUEST] %s player=%s(%s) server=%s allowed=%b",
            TIMESTAMP.format(Instant.now()), playerName, playerId, targetServer, allowed));
    }

    void logResult(UUID playerId, String playerName, String targetServer, boolean success, String reason) {
        write(String.format("[RESULT]  %s player=%s(%s) server=%s success=%b reason=%s",
            TIMESTAMP.format(Instant.now()), playerName, playerId, targetServer, success, reason));
    }

    void logRateLimit(UUID playerId, String playerName) {
        write(String.format("[RATELIMIT] %s player=%s(%s)",
            TIMESTAMP.format(Instant.now()), playerName, playerId));
    }

    void logCooldown(UUID playerId, String playerName, long remainingSeconds) {
        write(String.format("[COOLDOWN] %s player=%s(%s) remaining=%ds",
            TIMESTAMP.format(Instant.now()), playerName, playerId, remainingSeconds));
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
