package dev.azreyzaako.hopskiprtp.proxy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.yaml.snakeyaml.Yaml;

/**
 * Persists cooldowns to a YAML file so they survive proxy restarts.
 * Also provides an in-memory cache for fast lookups.
 */
final class CooldownStore {

    private final Path storePath;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    CooldownStore(Path dataDirectory) {
        this.storePath = dataDirectory.resolve("cooldowns.yml");
    }

    void load() {
        if (Files.notExists(storePath)) {
            return;
        }
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (java.io.InputStream input = Files.newInputStream(storePath)) {
                root = yaml.load(input);
            }
            if (root == null) {
                return;
            }
            Object data = root.get("cooldowns");
            if (!(data instanceof Map<?, ?> map)) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey().toString());
                    long expiresAt = ((Number) entry.getValue()).longValue();
                    if (expiresAt > now) {
                        cooldowns.put(playerId, expiresAt);
                    }
                } catch (Exception ignored) {
                    // Skip malformed entries
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load cooldown store.", e);
        }
    }

    void save() {
        try {
            Files.createDirectories(storePath.getParent());
            long now = System.currentTimeMillis();
            Map<String, Long> toSave = new java.util.HashMap<>();
            cooldowns.forEach((id, expires) -> {
                if (expires > now) {
                    toSave.put(id.toString(), expires);
                }
            });
            StringBuilder sb = new StringBuilder();
            sb.append("# HopSkipRTP cooldown persistence\n");
            sb.append("cooldowns:\n");
            for (Map.Entry<String, Long> entry : toSave.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            Files.writeString(storePath, sb.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save cooldown store.", e);
        }
    }

    void put(UUID playerId, long expiresAtMillis) {
        cooldowns.put(playerId, expiresAtMillis);
    }

    Optional<Duration> getRemaining(UUID playerId) {
        Long expires = cooldowns.get(playerId);
        if (expires == null) {
            return Optional.empty();
        }
        long remaining = expires - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(Duration.ofMillis(remaining));
    }

    void purgeExpired() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    void clear() {
        cooldowns.clear();
    }

    int size() {
        return cooldowns.size();
    }
}
