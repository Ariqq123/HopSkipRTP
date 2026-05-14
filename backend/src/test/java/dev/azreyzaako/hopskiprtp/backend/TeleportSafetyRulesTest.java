package dev.azreyzaako.hopskiprtp.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class TeleportSafetyRulesTest {

    @Test
    void checksWorldAllowListAndDimensions() {
        assertTrue(TeleportSafetyRules.isWorldAllowed("world", World.Environment.NORMAL, List.of("world"), false, false));
        assertFalse(TeleportSafetyRules.isWorldAllowed("world_nether", World.Environment.NETHER, List.of("world_nether"), false, false));
        assertTrue(TeleportSafetyRules.isWorldAllowed("world_nether", World.Environment.NETHER, List.of("world_nether"), true, false));
    }

    @Test
    void checksBiomeBlacklistCaseInsensitively() {
        assertFalse(TeleportSafetyRules.isBiomeAllowed("ocean", List.of("OCEAN")));
        assertTrue(TeleportSafetyRules.isBiomeAllowed("plains", List.of("OCEAN")));
    }

    @Test
    void checksSpawnProtectionAndGroundSafety() {
        World world = (World) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { World.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "world";
                case "getEnvironment" -> World.Environment.NORMAL;
                case "getSpawnLocation" -> new Location((World) proxy, 0, 64, 0);
                default -> {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == void.class) {
                        yield null;
                    }
                    if (returnType.isPrimitive()) {
                        yield java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(returnType, 1), 0);
                    }
                    yield null;
                }
            }
        );

        Location spawn = new Location(world, 100, 64, 100);
        assertTrue(TeleportSafetyRules.isInsideSpawnProtection(101, 101, spawn, 2));
        assertFalse(TeleportSafetyRules.isInsideSpawnProtection(120, 120, spawn, 2));
        assertTrue(TeleportSafetyRules.isSafeGround(Material.STONE));
        assertFalse(TeleportSafetyRules.isSafeGround(Material.LAVA));
    }
}
