package dev.azreyzaako.hopskiprtp.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProxyConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesAndPersistsSharedSecretOnFirstLoad() throws Exception {
        ProxyConfig first = ProxyConfig.load(tempDir);
        Path configPath = tempDir.resolve("config.yml");
        String rendered = Files.readString(configPath);

        assertTrue(first.sharedSecretGenerated());
        assertFalse(first.sharedSecret().isBlank());
        assertNotEquals("CHANGE_ME", first.sharedSecret());
        assertTrue(rendered.contains(first.sharedSecret()));

        ProxyConfig second = ProxyConfig.load(tempDir);
        assertFalse(second.sharedSecretGenerated());
        assertEquals(first.sharedSecret(), second.sharedSecret());
    }
}
