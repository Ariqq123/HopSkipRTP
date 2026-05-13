package dev.azreyzaako.hopskiprtp.backend;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

final class HopSkipRtpCommand implements CommandExecutor, TabCompleter {

    private final HopSkipRtpBackendPlugin plugin;

    HopSkipRtpCommand(HopSkipRtpBackendPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("hopskiprtp.admin.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload HopSkipRTP.");
                return true;
            }
            plugin.reloadFromCommand(sender);
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
