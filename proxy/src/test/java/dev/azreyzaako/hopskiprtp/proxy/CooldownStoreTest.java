package dev.azreyzaako.hopskiprtp.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsCooldownsAcrossReloads() {
        UUID playerId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();

        CooldownStore store = new CooldownStore(tempDir);
        store.put(playerId, expiresAt);
        store.save();

        CooldownStore reloaded = new CooldownStore(tempDir);
        reloaded.load();

        assertTrue(reloaded.getRemaining(playerId).isPresent());
        assertEquals(1, reloaded.size());
    }

    @Test
    void purgesExpiredEntries() {
        UUID playerId = UUID.randomUUID();

        CooldownStore store = new CooldownStore(tempDir);
        store.put(playerId, System.currentTimeMillis() - 1_000);
        store.purgeExpired();

        assertFalse(store.getRemaining(playerId).isPresent());
        assertEquals(0, store.size());
    }
}
