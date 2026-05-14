package dev.azreyzaako.hopskiprtp.backend;

import dev.azreyzaako.hopskiprtp.common.RtpProtocol;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopSkipRtpBackendPlugin extends JavaPlugin {

    private BackendConfig config;
    private TeleportSessionManager teleportSessionManager;
    private AuditLogger auditLogger;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadRuntimeConfig();

        teleportSessionManager = new TeleportSessionManager(this);
        getServer().getPluginManager().registerEvents(teleportSessionManager, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, RtpProtocol.CHANNEL, teleportSessionManager);
        getServer().getMessenger().registerOutgoingPluginChannel(this, RtpProtocol.CHANNEL);

        PluginCommand command = Objects.requireNonNull(getCommand("hopskiprtp"), "hopskiprtp command missing from plugin.yml");
        HopSkipRtpCommand rtpCommand = new HopSkipRtpCommand(this);
        command.setExecutor(rtpCommand);
        command.setTabCompleter(rtpCommand);

        getLogger().info("HopSkipRTP backend enabled.");
    }

    @Override
    public void onDisable() {
        if (teleportSessionManager != null) {
            teleportSessionManager.shutdown();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this, RtpProtocol.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, RtpProtocol.CHANNEL);
    }

    BackendConfig config() {
        return config;
    }

    void reloadRuntimeConfig() {
        reloadConfig();
        this.config = BackendConfig.load(this);
        this.auditLogger = config.auditLogging() ? new AuditLogger(getDataFolder().toPath()) : null;
    }

    AuditLogger auditLogger() {
        return auditLogger;
    }

    void reloadFromCommand(org.bukkit.command.CommandSender sender) {
        try {
            reloadRuntimeConfig();
            sender.sendMessage(BackendConfig.colorize(config.messages().reloadSuccess()));
        } catch (Exception exception) {
            sender.sendMessage(BackendConfig.colorize(config.messages().reloadFailed().replace("{reason}", exception.getMessage())));
        }
    }
}
