package dev.azreyzaako.hopskiprtp.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import org.yaml.snakeyaml.Yaml;

final class ProxyConfig {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final String sharedSecret;
    private final boolean sharedSecretGenerated;
    private final Permissions permissions;
    private final int cooldownSeconds;
    private final int warmupSeconds;
    private final double moveCancelDistanceBlocks;
    private final int requestTimeoutSeconds;
    private final boolean debug;
    private final List<String> allowedBackends;
    private final Messages messages;
    private final int rateLimitGlobalBurst;
    private final int rateLimitGlobalRefillPerSecond;
    private final int rateLimitPlayerBurst;
    private final int rateLimitPlayerRefillPerSecond;
    private final boolean auditLogging;

    private ProxyConfig(
        String sharedSecret,
        boolean sharedSecretGenerated,
        Permissions permissions,
        int cooldownSeconds,
        int warmupSeconds,
        double moveCancelDistanceBlocks,
        int requestTimeoutSeconds,
        boolean debug,
        List<String> allowedBackends,
        Messages messages,
        int rateLimitGlobalBurst,
        int rateLimitGlobalRefillPerSecond,
        int rateLimitPlayerBurst,
        int rateLimitPlayerRefillPerSecond,
        boolean auditLogging
    ) {
        this.sharedSecret = sharedSecret;
        this.sharedSecretGenerated = sharedSecretGenerated;
        this.permissions = permissions;
        this.cooldownSeconds = cooldownSeconds;
        this.warmupSeconds = warmupSeconds;
        this.moveCancelDistanceBlocks = moveCancelDistanceBlocks;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.debug = debug;
        this.allowedBackends = allowedBackends;
        this.messages = messages;
        this.rateLimitGlobalBurst = rateLimitGlobalBurst;
        this.rateLimitGlobalRefillPerSecond = rateLimitGlobalRefillPerSecond;
        this.rateLimitPlayerBurst = rateLimitPlayerBurst;
        this.rateLimitPlayerRefillPerSecond = rateLimitPlayerRefillPerSecond;
        this.auditLogging = auditLogging;
    }

    static ProxyConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path configPath = dataDirectory.resolve("config.yml");
        if (Files.notExists(configPath)) {
            try (InputStream input = ProxyConfig.class.getResourceAsStream("/config.yml")) {
                if (input == null) {
                    throw new IOException("Default proxy config resource is missing.");
                }
                Files.copy(input, configPath);
            }
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(configPath)) {
            root = yaml.load(input);
        }

        if (root == null) {
            throw new IOException("Proxy config is empty.");
        }

        String sharedSecret = readString(root, "shared-secret");
        boolean generatedSharedSecret = false;
        if (sharedSecret.isBlank() || "CHANGE_ME".equals(sharedSecret)) {
            sharedSecret = generateSharedSecret();
            root.put("shared-secret", sharedSecret);
            saveYaml(configPath, root);
            generatedSharedSecret = true;
        }

        List<String> allowedBackends = readStringList(root, "allowed-backends");
        if (allowedBackends.isEmpty()) {
            throw new IOException("Add at least one backend name to allowed-backends.");
        }

        Permissions permissions = new Permissions(
            readStringMap(root, "permissions", "use", "hopskiprtp.use"),
            readStringMap(root, "permissions", "bypass-cooldown", "hopskiprtp.bypass.cooldown"),
            readStringMap(root, "permissions", "admin-reload", "hopskiprtp.admin.reload")
        );

        Messages messages = new Messages(
            readNestedString(root, "messages", "no-permission", "&cYou do not have permission to use /rtp."),
            readNestedString(root, "messages", "not-a-player", "&cOnly players can use /rtp."),
            readNestedString(root, "messages", "no-backends", "&cNo eligible backend servers are available."),
            readNestedString(root, "messages", "cooldown-active", "&cYou must wait {seconds}s before using /rtp again."),
            readNestedString(root, "messages", "warmup-start", "&eRTP warmup started. Do not move."),
            readNestedString(root, "messages", "warmup-moved", "&cRTP cancelled because you moved."),
            readNestedString(root, "messages", "request-failed", "&cRTP failed: {reason}"),
            readNestedString(root, "messages", "request-timeout", "&cRTP timed out."),
            readNestedString(root, "messages", "reload-success", "&aHopSkipRTP reloaded."),
            readNestedString(root, "messages", "reload-failed", "&cHopSkipRTP reload failed: {reason}")
        );

        return new ProxyConfig(
            sharedSecret,
            generatedSharedSecret,
            permissions,
            readInt(root, "cooldown-seconds", 60),
            readInt(root, "warmup-seconds", 5),
            readDouble(root, "move-cancel-distance-blocks", 0.2),
            readInt(root, "request-timeout-seconds", 20),
            readBoolean(root, "debug", false),
            allowedBackends,
            messages,
            readNestedInt(root, "rate-limit", "global-burst", 100),
            readNestedInt(root, "rate-limit", "global-refill-per-second", 50),
            readNestedInt(root, "rate-limit", "player-burst", 3),
            readNestedInt(root, "rate-limit", "player-refill-per-second", 1),
            readBoolean(root, "audit-logging", true)
        );
    }

    String sharedSecret() {
        return sharedSecret;
    }

    boolean sharedSecretGenerated() {
        return sharedSecretGenerated;
    }

    Permissions permissions() {
        return permissions;
    }

    int cooldownSeconds() {
        return cooldownSeconds;
    }

    int warmupSeconds() {
        return warmupSeconds;
    }

    double moveCancelDistanceBlocks() {
        return moveCancelDistanceBlocks;
    }

    int requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    boolean debug() {
        return debug;
    }

    List<String> allowedBackends() {
        return allowedBackends;
    }

    Messages messages() {
        return messages;
    }

    int rateLimitGlobalBurst() {
        return rateLimitGlobalBurst;
    }

    int rateLimitGlobalRefillPerSecond() {
        return rateLimitGlobalRefillPerSecond;
    }

    int rateLimitPlayerBurst() {
        return rateLimitPlayerBurst;
    }

    int rateLimitPlayerRefillPerSecond() {
        return rateLimitPlayerRefillPerSecond;
    }

    boolean auditLogging() {
        return auditLogging;
    }

    private static String readString(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    private static String readStringMap(Map<String, Object> root, String parent, String key, String defaultValue) {
        Object value = root.get(parent);
        if (!(value instanceof Map<?, ?> map)) {
            return defaultValue;
        }
        Object nested = map.get(key);
        return nested == null ? defaultValue : nested.toString().trim();
    }

    private static String readNestedString(Map<String, Object> root, String parent, String key, String defaultValue) {
        Object value = root.get(parent);
        if (!(value instanceof Map<?, ?> map)) {
            return defaultValue;
        }
        Object nested = map.get(key);
        return nested == null ? defaultValue : nested.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).map(String::trim).filter(entry -> !entry.isBlank()).toList();
        }
        return List.of(value.toString().trim());
    }

    private static int readInt(Map<String, Object> root, String key, int defaultValue) {
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            return Integer.parseInt(value.toString().trim());
        }
        return defaultValue;
    }

    private static double readDouble(Map<String, Object> root, String key, double defaultValue) {
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            return Double.parseDouble(value.toString().trim());
        }
        return defaultValue;
    }

    private static int readNestedInt(Map<String, Object> root, String parent, String key, int defaultValue) {
        Object value = root.get(parent);
        if (!(value instanceof Map<?, ?> map)) {
            return defaultValue;
        }
        Object nested = map.get(key);
        if (nested instanceof Number number) {
            return number.intValue();
        }
        if (nested != null) {
            return Integer.parseInt(nested.toString().trim());
        }
        return defaultValue;
    }

    private static boolean readBoolean(Map<String, Object> root, String key, boolean defaultValue) {
        Object value = root.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString().trim());
        }
        return defaultValue;
    }

    private static String generateSharedSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private static void saveYaml(Path path, Map<String, Object> root) throws IOException {
        Yaml yaml = new Yaml();
        String rendered = yaml.dump(root);
        Files.writeString(path, rendered);
    }

    record Permissions(String use, String bypassCooldown, String adminReload) {
    }

    record Messages(
        String noPermission,
        String notAPlayer,
        String noBackends,
        String cooldownActive,
        String warmupStart,
        String warmupMoved,
        String requestFailed,
        String requestTimeout,
        String reloadSuccess,
        String reloadFailed
    ) {
    }
}
