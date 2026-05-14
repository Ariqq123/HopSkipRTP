package dev.azreyzaako.hopskiprtp.backend;

import dev.azreyzaako.hopskiprtp.common.RtpCodec;
import dev.azreyzaako.hopskiprtp.common.RtpRequest;
import dev.azreyzaako.hopskiprtp.common.RtpResponse;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

final class TeleportSessionManager implements Listener, PluginMessageListener {

    private static final EnumSet<Material> UNSAFE_GROUND = EnumSet.of(
        Material.CACTUS,
        Material.MAGMA_BLOCK,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        Material.POINTED_DRIPSTONE,
        Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE,
        Material.LAVA,
        Material.POWDER_SNOW,
        Material.NETHER_PORTAL,
        Material.END_PORTAL,
        Material.BARRIER
    );

    private final HopSkipRtpBackendPlugin plugin;
    private final Map<UUID, TeleportSession> sessionsByRequestId = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportSession> sessionsByPlayerId = new ConcurrentHashMap<>();

    TeleportSessionManager(HopSkipRtpBackendPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!dev.azreyzaako.hopskiprtp.common.RtpProtocol.CHANNEL.equals(channel)) {
            return;
        }

        BackendConfig config = plugin.config();
        java.util.Optional<RtpRequest> decoded = RtpCodec.decodeRequest(config.sharedSecret(), message);
        if (decoded.isEmpty()) {
            return;
        }

        RtpRequest request = decoded.get();
        if (!request.playerId().equals(player.getUniqueId())) {
            sendFailure(player, request.requestId(), "Player mismatch.");
            return;
        }

        if (!isWorldAllowed(player.getWorld(), config)) {
            sendFailure(player, request.requestId(), config.messages().requestDenied());
            return;
        }

        if (plugin.config().debug()) {
            plugin.getLogger().info("Received RTP request " + request.requestId() + " for " + player.getName() + " on " + player.getWorld().getName() + ".");
        }

        TeleportSession session = new TeleportSession(
            request.requestId(),
            request.playerId(),
            player.getName(),
            player.getWorld().getName(),
            player.getLocation().clone(),
            request.moveCancelDistanceBlocks()
        );

        sessionsByRequestId.put(request.requestId(), session);
        sessionsByPlayerId.put(request.playerId(), session);

        int warmupSeconds = Math.max(0, request.warmupSeconds());
        if (warmupSeconds == 0) {
            executeTeleport(session);
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> executeTeleport(session), warmupSeconds * 20L);
        session.setTask(task);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        TeleportSession session = sessionsByPlayerId.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getTo() == null || event.getTo().getWorld() == null || session.startLocation().getWorld() == null) {
            cancelSession(session, plugin.config().messages().warmupMoved());
            return;
        }

        if (!Objects.equals(session.startLocation().getWorld(), event.getTo().getWorld())) {
            cancelSession(session, plugin.config().messages().warmupMoved());
            return;
        }

        if (session.startLocation().distanceSquared(event.getTo()) > session.moveCancelDistanceBlocksSquared()) {
            cancelSession(session, plugin.config().messages().warmupMoved());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        TeleportSession session = sessionsByPlayerId.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getTo() == null || !Objects.equals(session.startLocation().getWorld(), event.getTo().getWorld())) {
            cancelSession(session, plugin.config().messages().warmupMoved());
            return;
        }

        if (session.startLocation().distanceSquared(event.getTo()) > session.moveCancelDistanceBlocksSquared()) {
            cancelSession(session, plugin.config().messages().warmupMoved());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        TeleportSession session = sessionsByPlayerId.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            sessionsByRequestId.remove(session.requestId());
            session.cancelTask();
            session.markFinished();
        }
    }

    void shutdown() {
        sessionsByRequestId.values().forEach(TeleportSession::cancelTask);
        sessionsByRequestId.clear();
        sessionsByPlayerId.clear();
    }

    private void executeTeleport(TeleportSession session) {
        if (!session.markFinished()) {
            return;
        }

        sessionsByRequestId.remove(session.requestId());
        sessionsByPlayerId.remove(session.playerId());
        session.cancelTask();

        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            return;
        }

        World world = player.getWorld();
        if (!isWorldAllowed(world, plugin.config())) {
            sendFailure(player, session.requestId(), plugin.config().messages().requestDenied());
            logAudit(player, world.getName(), 0, 0, false, "world-not-allowed");
            return;
        }

        Location destination = findSafeLocation(world, plugin.config(), player);
        if (destination == null) {
            sendFailure(player, session.requestId(), plugin.config().messages().noSafeLocation());
            logAudit(player, world.getName(), 0, 0, false, "no-safe-location");
            return;
        }

        boolean teleported = player.teleport(destination);
        if (!teleported) {
            sendFailure(player, session.requestId(), "Teleport failed.");
            logAudit(player, world.getName(), destination.getBlockX(), destination.getBlockZ(), false, "teleport-failed");
            return;
        }

        sendSuccess(player, session.requestId(), plugin.config().messages().success());
        logAudit(player, world.getName(), destination.getBlockX(), destination.getBlockZ(), true, "success");
    }

    private void logAudit(Player player, String worldName, int x, int z, boolean success, String reason) {
        AuditLogger logger = plugin.auditLogger();
        if (logger != null) {
            logger.logTeleport(player.getUniqueId(), player.getName(), worldName, x, z, success, reason);
        }
    }

    private void cancelSession(TeleportSession session, String reason) {
        if (!session.markFinished()) {
            return;
        }

        sessionsByRequestId.remove(session.requestId());
        sessionsByPlayerId.remove(session.playerId());
        session.cancelTask();

        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null && player.isOnline()) {
            sendFailure(player, session.requestId(), reason);
        }
    }

    private void sendSuccess(Player player, UUID requestId, String message) {
        sendResponse(player, requestId, true, message);
    }

    private void sendFailure(Player player, UUID requestId, String message) {
        sendResponse(player, requestId, false, message);
    }

    private void sendResponse(Player player, UUID requestId, boolean success, String message) {
        if (!player.isOnline()) {
            return;
        }

        player.sendPluginMessage(
            plugin,
            dev.azreyzaako.hopskiprtp.common.RtpProtocol.CHANNEL,
            RtpCodec.encodeResponse(
                plugin.config().sharedSecret(),
                new RtpResponse(requestId, player.getUniqueId(), success, message)
            )
        );
    }

    private boolean isWorldAllowed(World world, BackendConfig config) {
        if (!config.allowedWorlds().contains(world.getName())) {
            return false;
        }

        switch (world.getEnvironment()) {
            case NORMAL:
                return true;
            case NETHER:
                return config.allowNether();
            case THE_END:
                return config.allowEnd();
            default:
                return false;
        }
    }

    private Location findSafeLocation(World world, BackendConfig config, Player player) {
        int radius = Math.max(1, config.searchRadius());
        int attempts = Math.max(1, config.searchAttempts());
        WorldBorder border = world.getWorldBorder();
        Location spawn = world.getSpawnLocation();
        int spawnProtection = Math.max(0, config.spawnProtectionRadius());
        List<String> biomeBlacklist = config.biomeBlacklist();

        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = java.util.concurrent.ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = java.util.concurrent.ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            Location borderProbe = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z), z + 0.5);
            if (!border.isInside(borderProbe)) {
                continue;
            }

            // Spawn protection check
            if (spawnProtection > 0 && spawn != null && spawn.getWorld() != null
                && spawn.getWorld().equals(world)) {
                double dx = x - spawn.getBlockX();
                double dz = z - spawn.getBlockZ();
                if (dx * dx + dz * dz <= (long) spawnProtection * spawnProtection) {
                    continue;
                }
            }

            int groundY = world.getHighestBlockYAt(x, z);
            if (groundY <= world.getMinHeight() || groundY >= world.getMaxHeight() - 2) {
                continue;
            }

            // Biome blacklist check
            if (!biomeBlacklist.isEmpty()) {
                org.bukkit.block.Biome biome = world.getBiome(x, groundY, z);
                String biomeName = biome.getKey().getKey().toUpperCase(java.util.Locale.ROOT);
                if (biomeBlacklist.stream().map(String::toUpperCase).anyMatch(biomeName::equals)) {
                    continue;
                }
            }

            Block ground = world.getBlockAt(x, groundY, z);
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);

            if (!isSafeGround(ground) || !feet.isPassable() || !head.isPassable()) {
                continue;
            }

            return new Location(world, x + 0.5, groundY + 1, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        }

        return null;
    }

    private boolean isSafeGround(Block block) {
        Material type = block.getType();
        return type.isSolid() && !UNSAFE_GROUND.contains(type);
    }

    private static final class TeleportSession {
        private final UUID requestId;
        private final UUID playerId;
        private final String playerName;
        private final String worldName;
        private final Location startLocation;
        private final double moveCancelDistanceBlocks;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile BukkitTask task;

        private TeleportSession(UUID requestId, UUID playerId, String playerName, String worldName, Location startLocation, double moveCancelDistanceBlocks) {
            this.requestId = requestId;
            this.playerId = playerId;
            this.playerName = playerName;
            this.worldName = worldName;
            this.startLocation = startLocation;
            this.moveCancelDistanceBlocks = moveCancelDistanceBlocks;
        }

        private UUID requestId() {
            return requestId;
        }

        private UUID playerId() {
            return playerId;
        }

        private Location startLocation() {
            return startLocation;
        }

        private double moveCancelDistanceBlocksSquared() {
            return moveCancelDistanceBlocks * moveCancelDistanceBlocks;
        }

        private void setTask(BukkitTask task) {
            this.task = task;
        }

        private void cancelTask() {
            if (task != null) {
                task.cancel();
            }
        }

        private boolean markFinished() {
            return finished.compareAndSet(false, true);
        }
    }
}
