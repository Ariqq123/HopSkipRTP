package dev.azreyzaako.hopskiprtp.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.azreyzaako.hopskiprtp.common.RtpCodec;
import dev.azreyzaako.hopskiprtp.common.RtpProtocol;
import dev.azreyzaako.hopskiprtp.common.RtpRequest;
import dev.azreyzaako.hopskiprtp.common.RtpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;

@Plugin(id = "hopskiprtp", name = "HopSkipRTP", version = "0.1.0", authors = {"azreyzaako"})
public final class HopSkipRtpProxyPlugin {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(RtpProtocol.CHANNEL);
    private static final LegacyComponentSerializer COLOR = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private final Map<UUID, PendingTeleport> pendingByRequestId = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingByPlayerId = new ConcurrentHashMap<>();
    private final CooldownStore cooldownStore;
    private final Metrics metrics = new Metrics();
    private RateLimiter rateLimiter;
    private AuditLogger auditLogger;

    private volatile ProxyConfig config;
    private volatile ScheduledTask cooldownCleanupTask;
    private volatile ScheduledTask rateLimitCleanupTask;

    @Inject
    public HopSkipRtpProxyPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.cooldownStore = new CooldownStore(dataDirectory);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        reloadConfiguration();
        cooldownStore.load();

        CommandManager commandManager = proxyServer.getCommandManager();
        commandManager.register(
            commandManager.metaBuilder("rtp").aliases("hopskiprtp").build(),
            new RtpCommand(this)
        );

        ChannelRegistrar channelRegistrar = proxyServer.getChannelRegistrar();
        channelRegistrar.register(CHANNEL);

        cooldownCleanupTask = proxyServer.getScheduler()
            .buildTask(this, this::purgeExpiredCooldowns)
            .repeat(1, TimeUnit.MINUTES)
            .schedule();

        rateLimitCleanupTask = proxyServer.getScheduler()
            .buildTask(this, this::cleanupRateLimiters)
            .repeat(5, TimeUnit.MINUTES)
            .schedule();

        logger.info("HopSkipRTP proxy enabled. Loaded {} persisted cooldowns.", cooldownStore.size());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        PendingTeleport pending = pendingByPlayerId.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }

        if (!pending.targetServer().equalsIgnoreCase(event.getServer().getServerInfo().getName())) {
            failPending(pending.requestId(), "Connected to the wrong backend.");
            return;
        }

        dispatchRequest(event.getPlayer(), event.getServer(), pending);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection serverConnection)) {
            return;
        }

        Optional<RtpResponse> decoded = RtpCodec.decodeResponse(requireConfig().sharedSecret(), event.getData());
        if (decoded.isEmpty()) {
            if (config.debug()) {
                logger.debug("Ignored malformed or unauthorized RTP response from backend {}.", serverConnection.getServerInfo().getName());
            }
            return;
        }

        handleResponse(decoded.get());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        PendingTeleport pending = pendingByPlayerId.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            pendingByRequestId.remove(pending.requestId());
            if (pending.timeoutTask() != null) {
                pending.timeoutTask().cancel();
            }
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (cooldownCleanupTask != null) {
            cooldownCleanupTask.cancel();
        }
        if (rateLimitCleanupTask != null) {
            rateLimitCleanupTask.cancel();
        }
        pendingByRequestId.values().forEach(pending -> {
            if (pending.timeoutTask() != null) {
                pending.timeoutTask().cancel();
            }
        });
        pendingByRequestId.clear();
        pendingByPlayerId.clear();
        cooldownStore.save();
    }

    public void requestTeleport(Player player) {
        ProxyConfig currentConfig = requireConfig();
        if (!player.hasPermission(currentConfig.permissions().use())) {
            player.sendMessage(message(currentConfig.messages().noPermission()));
            return;
        }

        if (pendingByPlayerId.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("A teleport is already in progress."));
            return;
        }

        if (rateLimiter != null && !rateLimiter.tryAcquire(player.getUniqueId())) {
            player.sendMessage(Component.text("&cRTP rate limit exceeded. Please try again later."));
            metrics.incrementRateLimited();
            if (auditLogger != null) {
                auditLogger.logRateLimit(player.getUniqueId(), player.getUsername());
            }
            return;
        }

        if (!player.hasPermission(currentConfig.permissions().bypassCooldown())) {
            Optional<Duration> remaining = getCooldownRemaining(player.getUniqueId());
            if (remaining.isPresent()) {
                long seconds = Math.max(1, remaining.get().toSeconds());
                player.sendMessage(message(currentConfig.messages().cooldownActive(), "seconds", String.valueOf(seconds)));
                metrics.incrementCooldownBlocked();
                if (auditLogger != null) {
                    auditLogger.logCooldown(player.getUniqueId(), player.getUsername(), seconds);
                }
                return;
            }
        }

        metrics.incrementRequests();

        Optional<RegisteredServer> targetServer = selectTargetServer(player);
        if (targetServer.isEmpty()) {
            player.sendMessage(message(currentConfig.messages().noBackends()));
            return;
        }

        RegisteredServer selectedServer = targetServer.get();
        UUID requestId = UUID.randomUUID();
        PendingTeleport pending = new PendingTeleport(
            player.getUniqueId(),
            requestId,
            selectedServer.getServerInfo().getName(),
            null
        );

        ScheduledTask timeoutTask = proxyServer.getScheduler()
            .buildTask(this, () -> failPending(requestId, currentConfig.messages().requestTimeout()))
            .delay(currentConfig.requestTimeoutSeconds(), TimeUnit.SECONDS)
            .schedule();
        pending = pending.withTimeoutTask(timeoutTask);

        pendingByRequestId.put(requestId, pending);
        pendingByPlayerId.put(player.getUniqueId(), pending);

        player.sendMessage(message(currentConfig.messages().warmupStart()));

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        if (currentServer.isPresent() && currentServer.get().getServerInfo().getName().equalsIgnoreCase(selectedServer.getServerInfo().getName())) {
            dispatchRequest(player, selectedServer, pending);
            return;
        }

        proxyServer.getScheduler().buildTask(this, () -> {
            player.createConnectionRequest(selectedServer).connect().thenAccept(result -> {
                if (!result.isSuccessful()) {
                    failPending(requestId, "Backend connection failed: " + result.getStatus());
                    if (currentConfig.debug()) {
                        logger.debug("RTP connection failed for {} -> {} with status {}", player.getUsername(), selectedServer.getServerInfo().getName(), result.getStatus());
                    }
                }
            });
        }).schedule();
    }

    public void reloadConfiguration() {
        try {
            ProxyConfig loaded = ProxyConfig.load(dataDirectory);
            this.config = loaded;
            this.rateLimiter = new RateLimiter(
                loaded.rateLimitGlobalBurst(),
                loaded.rateLimitGlobalRefillPerSecond(),
                loaded.rateLimitPlayerBurst(),
                loaded.rateLimitPlayerRefillPerSecond()
            );
            if (loaded.auditLogging()) {
                this.auditLogger = new AuditLogger(dataDirectory);
            } else {
                this.auditLogger = null;
            }
            if (loaded.debug()) {
                logger.info("HopSkipRTP proxy config loaded with debug enabled.");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load proxy config: " + exception.getMessage(), exception);
        }
    }

    public void reloadFromCommand(com.velocitypowered.api.command.CommandSource sender) {
        try {
            reloadConfiguration();
            sender.sendMessage(message(requireConfig().messages().reloadSuccess()));
        } catch (Exception exception) {
            sender.sendMessage(message(requireConfig().messages().reloadFailed(), "reason", exception.getMessage()));
        }
    }

    void handleCommand(com.velocitypowered.api.command.CommandSource source, String[] args) {
        ProxyConfig currentConfig = requireConfig();
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!source.hasPermission(currentConfig.permissions().adminReload())) {
                source.sendMessage(message(currentConfig.messages().noPermission()));
                return;
            }
            reloadFromCommand(source);
            return;
        }

        if (!(source instanceof Player player)) {
            source.sendMessage(message(currentConfig.messages().notAPlayer()));
            return;
        }

        requestTeleport(player);
    }

    Component message(String raw) {
        return COLOR.deserialize(applyPlaceholders(raw, Map.of()));
    }

    Component message(String raw, String key, String value) {
        return COLOR.deserialize(applyPlaceholders(raw, Map.of(key, value)));
    }

    Component message(String raw, Map<String, String> placeholders) {
        return COLOR.deserialize(applyPlaceholders(raw, placeholders));
    }

    private void handleResponse(RtpResponse response) {
        PendingTeleport pending = pendingByRequestId.remove(response.requestId());
        if (pending == null) {
            return;
        }

        pendingByPlayerId.remove(pending.playerId());
        if (pending.timeoutTask() != null) {
            pending.timeoutTask().cancel();
        }

        Player player = proxyServer.getPlayer(pending.playerId()).orElse(null);
        if (player == null) {
            return;
        }

        if (response.success()) {
            cooldownStore.put(player.getUniqueId(), System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(requireConfig().cooldownSeconds()));
            player.sendMessage(message(response.message()));
            metrics.incrementSuccess();
            if (auditLogger != null) {
                auditLogger.logResult(player.getUniqueId(), player.getUsername(), pending.targetServer(), true, "success");
            }
            if (config.debug()) {
                logger.debug("RTP success for {} on {}.", player.getUsername(), pending.targetServer());
            }
        } else {
            player.sendMessage(message(requireConfig().messages().requestFailed(), "reason", response.message()));
            metrics.incrementFailure();
            if (auditLogger != null) {
                auditLogger.logResult(player.getUniqueId(), player.getUsername(), pending.targetServer(), false, response.message());
            }
            if (config.debug()) {
                logger.debug("RTP failed for {} on {}: {}", player.getUsername(), pending.targetServer(), response.message());
            }
        }
    }

    private void dispatchRequest(Player player, RegisteredServer server, PendingTeleport pending) {
        if (!pending.markDispatched()) {
            return;
        }

        RtpRequest request = new RtpRequest(
            pending.requestId(),
            player.getUniqueId(),
            player.getUsername(),
            requireConfig().warmupSeconds(),
            requireConfig().moveCancelDistanceBlocks()
        );

        boolean sent = player.getCurrentServer()
            .map(connection -> connection.sendPluginMessage(CHANNEL, RtpCodec.encodeRequest(requireConfig().sharedSecret(), request)))
            .orElse(false);

        if (!sent) {
            failPending(pending.requestId(), "Unable to deliver RTP request to backend.");
            return;
        }

        if (auditLogger != null) {
            auditLogger.logRequest(player.getUniqueId(), player.getUsername(), server.getServerInfo().getName(), true);
        }
        if (config.debug()) {
            logger.debug("Dispatched RTP request {} for {} to {}.", pending.requestId(), player.getUsername(), server.getServerInfo().getName());
        }
    }

    private Optional<RegisteredServer> selectTargetServer(Player player) {
        ProxyConfig currentConfig = requireConfig();
        Collection<RegisteredServer> allServers = proxyServer.getAllServers();
        List<RegisteredServer> allowedServers = allServers.stream()
            .filter(server -> currentConfig.allowedBackends().contains(server.getServerInfo().getName()))
            .collect(Collectors.toCollection(ArrayList::new));

        if (allowedServers.isEmpty()) {
            return Optional.empty();
        }

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        List<RegisteredServer> candidates = allowedServers.stream()
            .filter(server -> currentServer.map(connection -> !connection.getServer().getServerInfo().getName().equalsIgnoreCase(server.getServerInfo().getName())).orElse(true))
            .collect(Collectors.toCollection(ArrayList::new));

        List<RegisteredServer> effectiveCandidates = candidates.isEmpty() ? allowedServers : candidates;
        int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(effectiveCandidates.size());
        return Optional.of(effectiveCandidates.get(index));
    }

    private Optional<Duration> getCooldownRemaining(UUID playerId) {
        return cooldownStore.getRemaining(playerId);
    }

    private void purgeExpiredCooldowns() {
        cooldownStore.purgeExpired();
    }

    private void cleanupRateLimiters() {
        if (rateLimiter != null) {
            rateLimiter.cleanup();
        }
    }

    private void failPending(UUID requestId, String reason) {
        PendingTeleport pending = pendingByRequestId.remove(requestId);
        if (pending == null) {
            return;
        }

        pendingByPlayerId.remove(pending.playerId());
        if (pending.timeoutTask() != null) {
            pending.timeoutTask().cancel();
        }

        metrics.incrementFailure();

        Player player = proxyServer.getPlayer(pending.playerId()).orElse(null);
        if (player != null) {
            player.sendMessage(message(requireConfig().messages().requestFailed(), "reason", reason));
        }

        if (config.debug()) {
            logger.debug("RTP request {} failed: {}", requestId, reason);
        }
    }

    private ProxyConfig requireConfig() {
        ProxyConfig current = this.config;
        if (current == null) {
            throw new IllegalStateException("Proxy configuration not loaded yet.");
        }
        return current;
    }

    private static String applyPlaceholders(String raw, Map<String, String> placeholders) {
        String rendered = Objects.requireNonNullElse(raw, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", Objects.requireNonNullElse(entry.getValue(), ""));
        }
        return rendered;
    }

    private static final class PendingTeleport {
        private final UUID playerId;
        private final UUID requestId;
        private final String targetServer;
        private final AtomicBoolean dispatched = new AtomicBoolean(false);
        private final ScheduledTask timeoutTask;

        private PendingTeleport(UUID playerId, UUID requestId, String targetServer, ScheduledTask timeoutTask) {
            this.playerId = playerId;
            this.requestId = requestId;
            this.targetServer = targetServer;
            this.timeoutTask = timeoutTask;
        }

        private PendingTeleport withTimeoutTask(ScheduledTask timeoutTask) {
            return new PendingTeleport(playerId, requestId, targetServer, timeoutTask);
        }

        private boolean markDispatched() {
            return dispatched.compareAndSet(false, true);
        }

        private UUID playerId() {
            return playerId;
        }

        private UUID requestId() {
            return requestId;
        }

        private String targetServer() {
            return targetServer;
        }

        private ScheduledTask timeoutTask() {
            return timeoutTask;
        }
    }
}
