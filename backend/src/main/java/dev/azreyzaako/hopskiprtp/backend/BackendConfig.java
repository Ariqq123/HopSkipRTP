package dev.azreyzaako.hopskiprtp.backend;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

final class BackendConfig {

    private final String sharedSecret;
    private final List<String> allowedWorlds;
    private final int searchRadius;
    private final int searchAttempts;
    private final boolean allowNether;
    private final boolean allowEnd;
    private final boolean debug;
    private final Messages messages;
    private final List<String> biomeBlacklist;
    private final int spawnProtectionRadius;
    private final boolean auditLogging;

    private BackendConfig(
        String sharedSecret,
        List<String> allowedWorlds,
        int searchRadius,
        int searchAttempts,
        boolean allowNether,
        boolean allowEnd,
        boolean debug,
        Messages messages,
        List<String> biomeBlacklist,
        int spawnProtectionRadius,
        boolean auditLogging
    ) {
        this.sharedSecret = sharedSecret;
        this.allowedWorlds = allowedWorlds;
        this.searchRadius = searchRadius;
        this.searchAttempts = searchAttempts;
        this.allowNether = allowNether;
        this.allowEnd = allowEnd;
        this.debug = debug;
        this.messages = messages;
        this.biomeBlacklist = biomeBlacklist;
        this.spawnProtectionRadius = spawnProtectionRadius;
        this.auditLogging = auditLogging;
    }

    static BackendConfig load(HopSkipRtpBackendPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String sharedSecret = config.getString("shared-secret", "");
        if (sharedSecret.isBlank() || "CHANGE_ME".equals(sharedSecret)) {
            throw new IllegalStateException("Set a real shared-secret in backend/config.yml.");
        }

        List<String> allowedWorlds = config.getStringList("allowed-worlds");
        if (allowedWorlds.isEmpty()) {
            throw new IllegalStateException("Add at least one world name to allowed-worlds.");
        }

        List<String> biomeBlacklist = config.getStringList("biome-blacklist");

        return new BackendConfig(
            sharedSecret,
            allowedWorlds,
            config.getInt("search-radius", 5000),
            config.getInt("search-attempts", 32),
            config.getBoolean("allow-nether", false),
            config.getBoolean("allow-end", false),
            config.getBoolean("debug", false),
            new Messages(
                config.getString("messages.reload-success", "&aHopSkipRTP backend reloaded."),
                config.getString("messages.reload-failed", "&cHopSkipRTP backend reload failed: {reason}"),
                config.getString("messages.request-denied", "&cRTP is not enabled in this world."),
                config.getString("messages.no-safe-location", "&cNo safe teleport location was found."),
                config.getString("messages.warmup-moved", "&cRTP cancelled because you moved."),
                config.getString("messages.success", "&aRTP complete.")
            ),
            biomeBlacklist,
            config.getInt("spawn-protection-radius", 500),
            config.getBoolean("audit-logging", true)
        );
    }

    static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    String sharedSecret() {
        return sharedSecret;
    }

    List<String> allowedWorlds() {
        return allowedWorlds;
    }

    int searchRadius() {
        return searchRadius;
    }

    int searchAttempts() {
        return searchAttempts;
    }

    boolean allowNether() {
        return allowNether;
    }

    boolean allowEnd() {
        return allowEnd;
    }

    boolean debug() {
        return debug;
    }

    Messages messages() {
        return messages;
    }

    List<String> biomeBlacklist() {
        return biomeBlacklist;
    }

    int spawnProtectionRadius() {
        return spawnProtectionRadius;
    }

    boolean auditLogging() {
        return auditLogging;
    }

    record Messages(
        String reloadSuccess,
        String reloadFailed,
        String requestDenied,
        String noSafeLocation,
        String warmupMoved,
        String success
    ) {
    }
}
