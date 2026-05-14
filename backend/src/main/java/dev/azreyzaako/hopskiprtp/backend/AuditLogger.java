package dev.azreyzaako.hopskiprtp.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Simple audit logger for backend RTP events.
 */
final class AuditLogger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final Path logFile;

    AuditLogger(Path dataDirectory) {
        this.logFile = dataDirectory.resolve("audit.log");
    }

    void logTeleport(UUID playerId, String playerName, String worldName, int x, int z, boolean success, String reason) {
        write(String.format("[TELEPORT] %s player=%s(%s) world=%s x=%d z=%d success=%b reason=%s",
            TIMESTAMP.format(Instant.now()), playerName, playerId, worldName, x, z, success, reason));
    }

    void logDenied(UUID playerId, String playerName, String reason) {
        write(String.format("[DENIED]   %s player=%s(%s) reason=%s",
            TIMESTAMP.format(Instant.now()), playerName, playerId, reason));
    }

    private void write(String line) {
        try {
            Files.writeString(logFile, line + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Audit logging should never break the main flow
        }
    }
}
