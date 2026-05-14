package dev.azreyzaako.hopskiprtp.backend;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class TeleportSafetyRules {

    private TeleportSafetyRules() {
    }

    static boolean isWorldAllowed(String worldName, World.Environment environment, List<String> allowedWorlds, boolean allowNether, boolean allowEnd) {
        if (!allowedWorlds.contains(worldName)) {
            return false;
        }

        return switch (environment) {
            case NORMAL -> true;
            case NETHER -> allowNether;
            case THE_END -> allowEnd;
            default -> false;
        };
    }

    static boolean isBiomeAllowed(String biomeName, List<String> biomeBlacklist) {
        String normalizedBiome = biomeName.toUpperCase(Locale.ROOT);
        return biomeBlacklist.stream()
            .map(value -> value.toUpperCase(Locale.ROOT))
            .noneMatch(normalizedBiome::equals);
    }

    static boolean isInsideSpawnProtection(int x, int z, Location spawnLocation, int spawnProtectionRadius) {
        if (spawnProtectionRadius <= 0 || spawnLocation == null || spawnLocation.getWorld() == null) {
            return false;
        }

        double dx = x - spawnLocation.getBlockX();
        double dz = z - spawnLocation.getBlockZ();
        return dx * dx + dz * dz <= (double) spawnProtectionRadius * spawnProtectionRadius;
    }

    static boolean isSafeGround(Material type) {
        return !UNSAFE_GROUND.contains(type.name());
    }

    private static final Set<String> UNSAFE_GROUND = Set.of(
            "CACTUS",
            "MAGMA_BLOCK",
            "FIRE",
            "SOUL_FIRE",
            "CAMPFIRE",
            "SOUL_CAMPFIRE",
            "POINTED_DRIPSTONE",
            "SWEET_BERRY_BUSH",
            "WITHER_ROSE",
            "LAVA",
            "POWDER_SNOW",
            "NETHER_PORTAL",
            "END_PORTAL",
            "BARRIER"
    );
}
