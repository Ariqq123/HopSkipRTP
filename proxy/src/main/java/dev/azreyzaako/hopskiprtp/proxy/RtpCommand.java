package dev.azreyzaako.hopskiprtp.proxy;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;

public final class RtpCommand implements SimpleCommand {

    private final HopSkipRtpProxyPlugin plugin;

    public RtpCommand(HopSkipRtpProxyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        plugin.handleCommand(source, args);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length == 0) {
            return List.of("reload");
        }
        return List.of();
    }
}
